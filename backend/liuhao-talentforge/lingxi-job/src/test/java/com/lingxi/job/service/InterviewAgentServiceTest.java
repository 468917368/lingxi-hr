package com.lingxi.job.service;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.domain.Result;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import com.lingxi.job.agent.BaibaoxiangUserIdProvider;
import com.lingxi.job.domain.dto.request.GenerateQuestionsRequest;
import com.lingxi.job.feign.dto.InternalApplicationDTO;
import com.lingxi.job.feign.dto.ResumeDetailDTO;
import com.lingxi.job.feign.ResumeDetailFeignClient;
import com.lingxi.job.feign.ResumeFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Interview Agent 服务 userId 捕获测试
 * <p>验证请求线程捕获内部用户 ID → 调用 {@link BaibaoxiangUserIdProvider} 生成伪标识 →
 * 显式传给客户端；异步 SSE 线程不读取 {@link UserContext}。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@SpringBootTest(properties = {
        "spring.cloud.bootstrap.enabled=false",
        "baibaoxiang.interview-app-id=test-interview-app"
})
class InterviewAgentServiceTest {

    private static final long TEST_COMPANY_ID = 888888L;

    @Autowired
    private InterviewAgentService interviewAgentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("agentSseSemaphore")
    private Semaphore semaphore;

    @MockBean
    private ResumeFeignClient resumeFeignClient;

    @MockBean
    private ResumeDetailFeignClient resumeDetailFeignClient;

    @MockBean
    private BaibaoxiangAgentClient baibaoxiangAgentClient;

    @SpyBean
    private BaibaoxiangUserIdProvider userIdProvider;

    @BeforeEach
    void setUp() {
        // 模拟网关注入的 HR 用户上下文（请求线程）
        UserDTO user = new UserDTO();
        user.setUserId(1001L);
        user.setRole("HR");
        user.setCompanyId(TEST_COMPANY_ID);
        UserContext.set(user);
        Mockito.reset(resumeFeignClient, resumeDetailFeignClient, baibaoxiangAgentClient, userIdProvider);
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        UserContext.clear();
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
        // 复位信号量（避免异常用例泄漏影响后续）
        int missing = 10 - semaphore.availablePermits();
        if (missing > 0) {
            semaphore.release(missing);
        }
    }

    @Test
    void generate_capturesUserIdBeforeAsync() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(888001L, jobId);

        List<String> receivedUserId = new ArrayList<>();
        AtomicBoolean userContextLeaked = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            String userId = invocation.getArgument(1);
            receivedUserId.add(userId);
            // 异步线程不应读取到 UserContext（请求线程捕获后显式传参）
            if (UserContext.getUserId() != null) {
                userContextLeaked.set(true);
            }
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-capture"));
            // 纯 AI 收紧（恰好 4 个标准题型各一）：stub 须输出 4 题，否则触发重试（streamInterview 被调 2 次）
            consumer.accept(AgentStreamEvent.content(
                    "{\"questions\":[{\"content\":\"q1\",\"type\":\"BASIC\",\"difficulty\":\"MEDIUM\"},"
                            + "{\"content\":\"q2\",\"type\":\"PROJECT\",\"difficulty\":\"MEDIUM\"},"
                            + "{\"content\":\"q3\",\"type\":\"BOUNDARY\",\"difficulty\":\"MEDIUM\"},"
                            + "{\"content\":\"q4\",\"type\":\"COMPREHENSIVE\",\"difficulty\":\"MEDIUM\"}]}"));
            consumer.accept(AgentStreamEvent.complete("req-capture"));
            latch.countDown();
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());

        GenerateQuestionsRequest req = new GenerateQuestionsRequest();
        req.setApplicationId(888001L);
        SseEmitter emitter = interviewAgentService.generateQuestions(jobId, req);
        assertNotNull(emitter);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "异步流应在超时前完成");
        // provider 按请求线程捕获的内部用户 ID + 目标 AppID 生成伪标识
        verify(userIdProvider).provide(1001L, "test-interview-app");
        assertEquals(1, receivedUserId.size());
        assertEquals("local-mock-user", receivedUserId.get(0), "客户端应收到 provider 生成的伪标识");
        assertFalse(receivedUserId.get(0).contains("1001"), "不应回传原始内部用户 ID");
        assertFalse(userContextLeaked.get(), "异步线程不得读取 UserContext");
    }

    /** Mock ResumeFeignClient（投递详情）+ ResumeDetailFeignClient（简历卡片）两步组装 */
    private void mockContext(Long appId, Long jobId) {
        InternalApplicationDTO app = new InternalApplicationDTO();
        app.setId(appId);
        app.setJobId(jobId);
        app.setCompanyId(TEST_COMPANY_ID);
        app.setCandidateId(1001L);
        app.setResumeId(1L);
        app.setStatus("SCREENED");
        when(resumeFeignClient.getApplication(appId)).thenReturn(Result.success(app));
        when(resumeDetailFeignClient.getResumeDetail(1L)).thenReturn(Result.success(resumeDetail()));
    }

    /** 简历卡片：{"sections":[{title, points:[{text}]}]} */
    private ResumeDetailDTO resumeDetail() {
        ResumeDetailDTO rd = new ResumeDetailDTO();
        rd.setId(1L);
        rd.setParseStatus("PARSED");
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "项目经历");
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(Collections.singletonMap("text", "负责 Java 后端开发"));
        section.put("points", points);
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("sections", sections);
        rd.setCardStructure(card);
        return rd;
    }

    private Long insertJob(long companyId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, status, created_by) "
                            + "VALUES (?, ?, 'IT', 'IT', '110000', '北京', 2, 'BACHELOR', 'PUBLISHED', 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "Java后端工程师");
            return ps;
        }, keyHolder);
        Long jobId = keyHolder.getKey().longValue();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_profile (company_id, job_id, job_type, core_skills, soft_skills, "
                            + "profile_source, version) VALUES (?, ?, 'JAVA_BACKEND', ?, ?, 'MANUAL', 1)");
            ps.setLong(1, companyId);
            ps.setLong(2, jobId);
            ps.setString(3, "[{\"name\":\"Java\",\"level\":\"3\",\"required\":true}]");
            ps.setString(4, "[{\"name\":\"沟通\",\"importance\":\"3\"}]");
            return ps;
        });
        return jobId;
    }
}

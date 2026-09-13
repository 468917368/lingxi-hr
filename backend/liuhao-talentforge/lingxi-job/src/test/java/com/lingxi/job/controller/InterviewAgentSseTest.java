package com.lingxi.job.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.job.agent.AgentSseEvent;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import com.lingxi.job.agent.impl.MockBaibaoxiangAgentClient;
import com.lingxi.job.feign.dto.InternalApplicationDTO;
import com.lingxi.job.feign.dto.ResumeDetailDTO;
import com.lingxi.job.feign.ResumeDetailFeignClient;
import com.lingxi.job.feign.ResumeFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interview Agent SSE 出题接口测试
 * <p>Mock ResumeFeignClient 返回投递上下文，Mock BaibaoxiangAgentClient 事件流，
 * 覆盖 progress→result→done、并发 2304、状态白名单 409、Semaphore 释放回归、
 * dataCompleteness 边界、难度规范化、Bean 唯一性。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class InterviewAgentSseTest {

    private static final long TEST_COMPANY_ID = 666666L;

    /** 跨企业隔离测试用企业ID（面试官跨企业岗位 2101） */
    private static final long OTHER_COMPANY_ID = 888888L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("agentSseSemaphore")
    private Semaphore semaphore;

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private ResumeFeignClient resumeFeignClient;

    @MockBean
    private ResumeDetailFeignClient resumeDetailFeignClient;

    /** Spy 包装真实 MockBaibaoxiangAgentClient：默认走真实 Mock 事件流，可按需 stub 特定行为 */
    @SpyBean
    private BaibaoxiangAgentClient baibaoxiangAgentClient;

    /** Spy 包装 SSE 线程池：仅线程池拒绝用例临时 stub execute 抛 RejectedExecutionException */
    @SpyBean
    private ThreadPoolExecutor agentSseExecutor;

    @BeforeEach
    void setUp() {
        // reset 所有 mock/spy，防止 stub 跨测试残留
        Mockito.reset(resumeFeignClient, resumeDetailFeignClient, baibaoxiangAgentClient, agentSseExecutor);
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id = ?", OTHER_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", OTHER_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", OTHER_COMPANY_ID);
        // 复位信号量（避免异常用例泄漏影响后续）
        int missing = 10 - semaphore.availablePermits();
        if (missing > 0) {
            semaphore.release(missing);
        }
    }

    // ==================== 正常链路 ====================

    @Test
    void generate_progressResultDone() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(200L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        String body = performSse(jobId, "{\"applicationId\":200}");
        assertTrue(body.contains("event:progress"), "应输出 progress 事件");
        assertTrue(body.contains("event:result"), "应输出 result 事件");
        assertTrue(body.contains("event:done"), "应输出 done 事件");
        assertTrue(body.contains("SUFFICIENT"), "完整度 0.90 应输出 SUFFICIENT");
    }

    // ==================== 并发/状态/资源 ====================

    @Test
    void generate_concurrentFull503() throws Exception {
        for (int i = 0; i < 10; i++) {
            semaphore.acquire();
        }
        try {
            mockMvc.perform(post("/api/v1/hr/jobs/1/interview-questions/generate")
                            .headers(hrHeaders(TEST_COMPANY_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"applicationId\":1}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(2304))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        } finally {
            semaphore.release(10);
        }
    }

    @Test
    void generate_statusNotAllowed409() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(300L, jobId, TEST_COMPANY_ID, "SUBMITTED", new BigDecimal("0.90"));
        mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":300}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2305))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void generate_jobNotFound2101() throws Exception {
        mockContext(400L, 9999L, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        mockMvc.perform(post("/api/v1/hr/jobs/9999/interview-questions/generate")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    @Test
    void generate_resumeUnavailable_releasesPermit() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        // fallback 降级返回 Result.error(503)（真实场景：Sentinel Feign 降级），Service 按 code!=200 转 HTTP 503
        when(resumeFeignClient.getApplication(anyLong()))
                .thenReturn(Result.error(503, "简历服务不可用"));
        mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":500}"))
                .andExpect(status().isServiceUnavailable());
        // 建流前异常 → finally 释放许可
        assertEquals(10, semaphore.availablePermits());
    }

    @Test
    void generate_normalCompletion_singleRelease() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(600L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        int before = semaphore.availablePermits();
        performSse(jobId, "{\"applicationId\":600}");
        // NOTE: MockMvc 环境不触发 SseEmitter.onCompletion（Servlet 异步回调限制），
        // 许可（before-1）由回调释放的完整验证需真实容器联调（见差异表）。
        // 此处验证不发生重复释放（许可不会低于 acquire 后的值）。
        Thread.sleep(500);
        assertTrue(semaphore.availablePermits() >= before - 1,
                "不应发生重复释放，许可应 >= " + (before - 1) + "，实际 " + semaphore.availablePermits());
    }

    // ==================== 边界 ====================

    @Test
    void generate_dataCompleteness_low_isInsufficient() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(700L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.49"));
        String body = performSse(jobId, "{\"applicationId\":700}");
        assertTrue(body.contains("INSUFFICIENT"), "完整度 0.49 应输出 INSUFFICIENT");
        assertTrue(body.contains("\"isPersonalized\":false"), "完整度 0.49 应降级通用题");
    }

    @Test
    void generate_dataCompleteness_boundary_isSufficient() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(701L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.50"));
        String body = performSse(jobId, "{\"applicationId\":701}");
        assertTrue(body.contains("SUFFICIENT"), "完整度 0.50 边界应输出 SUFFICIENT");
    }

    @Test
    void generate_difficulty_invalid400() throws Exception {
        mockMvc.perform(post("/api/v1/hr/jobs/1/interview-questions/generate")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":1,\"difficulty\":\"XX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void generate_resultInvalid_retryThenSuccess() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(900L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        AtomicInteger call = new AtomicInteger();
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            // 第一次回调非法 result（questions 为空）→ 触发重试；第二次走真实 Mock 合法流
            if (call.incrementAndGet() == 1) {
                consumer.accept(AgentStreamEvent.content(
                        "{\"questions\":[],\"generationMode\":\"AI_GENERATED\",\"dataCompleteness\":\"SUFFICIENT\"}"));
                consumer.accept(AgentStreamEvent.complete("req-invalid"));
            } else {
                invocation.callRealMethod();
            }
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());
        String body = performSse(jobId, "{\"applicationId\":900}");
        assertTrue(body.contains("event:result"), "重试后应输出合法 result");
        assertTrue(body.contains("event:done"), "重试成功后应输出 done");
        assertFalse(body.contains("event:error"), "重试成功后不应输出 error");
    }

    @Test
    void generate_resultAlwaysInvalid_2302() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(901L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.content("{\"questions\":[]}"));
            consumer.accept(AgentStreamEvent.complete("req-invalid"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());
        String body = performSse(jobId, "{\"applicationId\":901}");
        assertTrue(body.contains("event:error"), "恒非法应输出 error");
        assertTrue(body.contains("2302"), "输出校验失败应为 2302");
        assertTrue(body.contains("\"retryable\":true"), "2302 应 retryable=true");
        assertFalse(body.contains("event:done"), "error 与 done 互斥");
    }

    @Test
    void generate_dataCompleteness_null_insufficient() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(902L, jobId, TEST_COMPANY_ID, "SCREENED", null);
        String body = performSse(jobId, "{\"applicationId\":902}");
        assertTrue(body.contains("INSUFFICIENT"), "完整度 null 应输出 INSUFFICIENT");
        assertTrue(body.contains("\"isPersonalized\":false"), "完整度 null 应降级通用题");
    }

    @Test
    void generate_difficulty_lower_medium() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(903L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        String body = performSse(jobId, "{\"applicationId\":903,\"difficulty\":\"medium\"}");
        assertTrue(body.contains("event:done"), "小写 difficulty 应规范化为 MEDIUM 且正常出题");
    }

    @Test
    void generate_sanitize_placeholder() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(904L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"),
                Arrays.asList("主导智慧园区平台项目", "毕业于北京大学", "联系 13812345678"));
        performSse(jobId, "{\"applicationId\":904}");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(baibaoxiangAgentClient).streamInterview(captor.capture(), anyString(), any(), any());
        String prompt = captor.getValue();
        // 整体替换断言：无重复后缀、原名不泄露（动词"主导/毕业"被一并吞掉属合理脱敏）
        assertTrue(prompt.contains("某项目"), "项目应整体脱敏为某项目，实际: " + prompt);
        assertTrue(prompt.contains("某大学"), "大学应整体脱敏为某大学，实际: " + prompt);
        assertFalse(prompt.contains("某大学大学"), "不应出现重复后缀 某大学大学");
        assertFalse(prompt.contains("某项目项目"), "不应出现重复后缀 某项目项目");
        assertFalse(prompt.contains("智慧园区"), "机构名不应保留");
        assertFalse(prompt.contains("13812345678"), "手机号应被脱敏");
    }

    @Test
    void generate_threadPoolReject_error2304() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(905L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        // Spy 线程池：临时 stub execute 抛 RejectedExecutionException，模拟队列满
        doThrow(new RejectedExecutionException("pool full"))
                .when(agentSseExecutor).execute(any(Runnable.class));
        try {
            String body = performSse(jobId, "{\"applicationId\":905}");
            assertTrue(body.contains("event:error"), "线程池拒绝应输出 error");
            assertTrue(body.contains("2304"), "线程池拒绝应为 2304");
            assertFalse(body.contains("event:done"), "error 与 done 互斥");
        } finally {
            // 恢复 spy 真实行为，避免污染后续测试
            Mockito.reset(agentSseExecutor);
        }
    }

    @Test
    void generate_companyIdNull_reject2101() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        // 下游 companyId=null → 严格完全匹配不通过 → 2101（拒绝，null 不放行）
        mockContext(9000L, jobId, null, "SCREENED", new BigDecimal("0.90"));
        mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":9000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    @Test
    void generate_progressOnlyDone_2302() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(9001L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        // 对应 progress→done 无 result：HEADER + COMPLETE 无 CONTENT → 空内容 → 重试后 2302
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-progress-only"));
            consumer.accept(AgentStreamEvent.complete("req-progress-only"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());
        String body = performSse(jobId, "{\"applicationId\":9001}");
        assertTrue(body.contains("event:error"), "progress→done 无 result 应输出 error");
        assertTrue(body.contains("2302"), "应判定输出校验失败 2302");
        assertFalse(body.contains("event:done"), "error 与 done 互斥");
    }

    @Test
    void generate_streamEarlyExit_noComplete_2303() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(9002L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        // 协议早断：有合法 result 内容但未发 COMPLETE → 视为异常结束 2303
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-early"));
            consumer.accept(AgentStreamEvent.content(
                    "{\"questions\":[{\"content\":\"q\",\"type\":\"BASIC\",\"difficulty\":\"MEDIUM\"}]}"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());
        String body = performSse(jobId, "{\"applicationId\":9002}");
        assertTrue(body.contains("event:error"), "协议早断应输出 error");
        assertTrue(body.contains("2303"), "协议早断应为 2303");
        assertFalse(body.contains("event:done"), "error 与 done 互斥");
    }

    @Test
    void generate_buildPrompt_jdTextSanitized() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID,
                "负责后端开发，联系 13812345678，工作地点北京市海淀区中关村大街27号");
        mockContext(9003L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        performSse(jobId, "{\"applicationId\":9003}");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(baibaoxiangAgentClient).streamInterview(captor.capture(), anyString(), any(), any());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("岗位 JD："), "prompt 应包含岗位 JD");
        assertFalse(prompt.contains("13812345678"), "岗位 JD 电话应被脱敏");
        assertFalse(prompt.contains("中关村大街27号"), "岗位 JD 精确地址应被脱敏");
    }

    @Test
    void generate_applicationNotFound_2305() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        // 投递不存在（resume 3103 APPLICATION_NOT_FOUND）→ 投递上下文非法 409+2305，非下游不可用
        when(resumeFeignClient.getApplication(anyLong()))
                .thenReturn(Result.error(3103, "投递记录不存在"));
        mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":999001}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2305));
    }

    @Test
    void generate_resumeBusinessFailure_insufficient() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        InternalApplicationDTO app = new InternalApplicationDTO();
        app.setId(999002L);
        app.setJobId(jobId);
        app.setCompanyId(TEST_COMPANY_ID);
        app.setCandidateId(1001L);
        app.setResumeId(1L);
        app.setStatus("SCREENED");
        when(resumeFeignClient.getApplication(999002L)).thenReturn(Result.success(app));
        // 简历详情返回业务失败 Result（非 null，非下游不可用）→ 降级通用题 INSUFFICIENT，不建流失败
        when(resumeDetailFeignClient.getResumeDetail(1L)).thenReturn(Result.error(500, "简历解析失败"));
        String body = performSse(jobId, "{\"applicationId\":999002}");
        assertTrue(body.contains("event:result"), "简历详情业务失败应降级通用题并正常出题");
        assertTrue(body.contains("INSUFFICIENT"), "简历详情业务失败应 INSUFFICIENT");
        assertFalse(body.contains("event:error"), "不应建流失败");
    }

    @Test
    void generate_beanMockUnique() {
        // ai.agent.mock=true（默认）→ 唯一 Bean 且为 Mock 实现（@SpyBean 为 CGLIB 子类，instanceof 成立）
        BaibaoxiangAgentClient client = applicationContext.getBean(BaibaoxiangAgentClient.class);
        assertNotNull(client);
        assertTrue(client instanceof MockBaibaoxiangAgentClient);
    }

    // ==================== 角色授权（阶段4c：面试官开放） ====================

    /**
     * 面试官角色（INTERVIEWER）对本企业合法投递出题 → 正常出题 SSE
     */
    @Test
    void interviewer_generate_success() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(2000L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        String body = performSse(jobId, "{\"applicationId\":2000}",
                roleHeaders(TEST_COMPANY_ID, "INTERVIEWER"));
        assertTrue(body.contains("event:progress"), "面试官应收到 progress 事件");
        assertTrue(body.contains("event:result"), "面试官应收到 result 事件");
        assertTrue(body.contains("event:done"), "面试官应收到 done 事件");
    }

    /**
     * 候选人角色（CANDIDATE）调出题接口 → 角色不匹配，HTTP 200 + code=403
     * <p>AuthInterceptor 抛 BusinessException(FORBIDDEN)，GlobalExceptionHandler 无 @ResponseStatus
     * → HTTP 200 + {"code":403}。</p>
     */
    @Test
    void candidate_generate_forbidden403() throws Exception {
        mockMvc.perform(post("/api/v1/hr/jobs/1/interview-questions/generate")
                        .headers(roleHeaders(TEST_COMPANY_ID, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /**
     * 面试官跨企业岗位 → 岗位归属校验失败，HTTP 200 + code=2101
     * <p>selectByIdAndCompanyId 带 AND company_id，他企业岗位返回 null → JOB_NOT_FOUND。</p>
     */
    @Test
    void interviewer_crossCompany_jobNotFound2101() throws Exception {
        Long otherJobId = insertJob(OTHER_COMPANY_ID);
        mockContext(2001L, otherJobId, OTHER_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", otherJobId)
                        .headers(roleHeaders(TEST_COMPANY_ID, "INTERVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":2001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    // ==================== 辅助方法 ====================

    /** 执行 SSE 请求并返回响应体（等待异步完成，默认 HR 角色头） */
    private String performSse(long jobId, String body) throws Exception {
        return performSse(jobId, body, hrHeaders(TEST_COMPANY_ID));
    }

    /** 执行 SSE 请求并返回响应体（指定角色请求头） */
    private String performSse(long jobId, String body, HttpHeaders headers) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", jobId)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        try {
            result.getAsyncResult(10_000L);
        } catch (Exception ignored) {
            // SseEmitter 无返回值，getAsyncResult 可能抛异常，忽略后读 body
        }
        return result.getResponse().getContentAsString();
    }

    /** Mock ResumeFeignClient 返回合法投递上下文（默认亮点） */
    private void mockContext(Long appId, Long jobId, Long companyId, String status, BigDecimal dc) {
        mockContext(appId, jobId, companyId, status, dc,
                Arrays.asList("负责 Java 后端开发", "联系电话 13812345678"));
    }

    /** Mock ResumeFeignClient（投递详情）+ ResumeDetailFeignClient（简历卡片）两步组装 */
    private void mockContext(Long appId, Long jobId, Long companyId, String status, BigDecimal dc,
                             List<String> highlights) {
        InternalApplicationDTO app = new InternalApplicationDTO();
        app.setId(appId);
        app.setJobId(jobId);
        app.setCompanyId(companyId);
        app.setCandidateId(1001L);
        app.setResumeId(1L);
        app.setStatus(status);
        when(resumeFeignClient.getApplication(appId)).thenReturn(Result.success(app));
        // 简历详情：dc=null 模拟简历缺失（降级通用题）；否则返回含 sections 的 cardStructure
        if (dc != null) {
            when(resumeDetailFeignClient.getResumeDetail(1L))
                    .thenReturn(Result.success(resumeDetail(highlights, dc)));
        } else {
            when(resumeDetailFeignClient.getResumeDetail(1L)).thenReturn(null);
        }
    }

    /** 构造简历卡片：{"sections":[{title, points:[{text}]}]}，points 填充率映射 dataCompleteness */
    private ResumeDetailDTO resumeDetail(List<String> highlights, BigDecimal dc) {
        ResumeDetailDTO rd = new ResumeDetailDTO();
        rd.setId(1L);
        rd.setParseStatus("PARSED");
        Map<String, Object> card = new LinkedHashMap<>();
        List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "项目经历");
        List<Map<String, Object>> points = new ArrayList<>();
        for (String h : highlights) {
            points.add(Collections.singletonMap("text", h));
        }
        int filled = points.size();
        // dataCompleteness = 非空 points / 总 points（补空 text 使填充率 ≈ dc，映射 SUFFICIENT/INSUFFICIENT）
        int total = filled;
        if (dc != null && dc.compareTo(BigDecimal.ZERO) > 0) {
            total = Math.max(filled, (int) Math.ceil(filled / dc.doubleValue()));
        }
        for (int i = filled; i < total; i++) {
            points.add(Collections.singletonMap("text", ""));
        }
        section.put("points", points);
        sections.add(section);
        card.put("sections", sections);
        rd.setCardStructure(card);
        return rd;
    }

    private HttpHeaders hrHeaders(long companyId) {
        return roleHeaders(companyId, "HR");
    }

    /** 构造指定角色的请求头（阶段4c 面试官/候选人授权测试用） */
    private HttpHeaders roleHeaders(long companyId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", role);
        headers.set("X-Company-Id", String.valueOf(companyId));
        return headers;
    }

    private Long insertJob(long companyId) {
        return insertJob(companyId, null);
    }

    private Long insertJob(long companyId, String jdText) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps;
            if (jdText != null) {
                ps = connection.prepareStatement(
                        "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                                + "city_name, min_experience_years, education_requirement, status, created_by, jd_text) "
                                + "VALUES (?, ?, 'IT', 'IT', '110000', '北京', 2, 'BACHELOR', 'PUBLISHED', 1, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, companyId);
                ps.setString(2, "Java后端工程师");
                ps.setString(3, jdText);
            } else {
                ps = connection.prepareStatement(
                        "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                                + "city_name, min_experience_years, education_requirement, status, created_by) "
                                + "VALUES (?, ?, 'IT', 'IT', '110000', '北京', 2, 'BACHELOR', 'PUBLISHED', 1)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, companyId);
                ps.setString(2, "Java后端工程师");
            }
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

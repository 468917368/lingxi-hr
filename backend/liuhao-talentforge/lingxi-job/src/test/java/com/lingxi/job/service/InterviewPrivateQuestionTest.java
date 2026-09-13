package com.lingxi.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import com.lingxi.job.domain.dto.query.QuestionSearchQuery;
import com.lingxi.job.feign.dto.InternalApplicationDTO;
import com.lingxi.job.feign.dto.ResumeDetailDTO;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.feign.ResumeDetailFeignClient;
import com.lingxi.job.feign.ResumeFeignClient;
import com.lingxi.job.mapper.JobQuestionMapper;
import com.lingxi.job.service.impl.InterviewAgentServiceImpl;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * 企业私有题确定性选题 + AI 补题测试（阶段4收尾）
 * <p>@SpyBean JobQuestionMapper（默认真实查询，可 stub 抛异常）；@SpyBean BaibaoxiangAgentClient
 * 走真实 Mock 事件流（可 stub 越界输出）；真实连接本地 MySQL，自建 job_post/job_profile/job_question 并清理。</p>
 *
 * @author lingxi-team
 * @since 2026-08-05
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class InterviewPrivateQuestionTest {

    /** 测试专用企业ID（企业 A）；888888 为企业 B（跨企业隔离用例） */
    private static final long TEST_COMPANY_ID = 777777L;
    private static final long OTHER_COMPANY_ID = 888888L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("agentSseSemaphore")
    private Semaphore semaphore;

    @MockBean
    private ResumeFeignClient resumeFeignClient;

    @MockBean
    private ResumeDetailFeignClient resumeDetailFeignClient;

    /** Mock 客户端：完全 stub（测试不依赖生产 Mock 客户端实现），合法结果用 stubValidAi() */
    @MockBean
    private BaibaoxiangAgentClient baibaoxiangAgentClient;

    /** Spy 题库 Mapper：默认真实查询，异常用例临时 stub 抛异常 */
    @SpyBean
    private JobQuestionMapper jobQuestionMapper;

    @AfterEach
    void cleanup() throws InterruptedException {
        // 本测试类可能写入企业 A 与企业 B（跨企业用例）
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id = ?", OTHER_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
        // 复位信号量（避免异常用例泄漏影响后续）
        int missing = 10 - semaphore.availablePermits();
        if (missing > 0) {
            semaphore.release(missing);
        }
    }

    // ==================== 确定性选题分支 ====================

    /** 全命中：4 题型各有 ACTIVE 题 → 4 题全 COMPANY_LIBRARY、有 progress、不调 AI */
    @Test
    void fullHit_companyLibrary() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "PROJECT", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "BOUNDARY", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "COMPREHENSIVE", "ACTIVE");
        mockContext(2001L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));

        String body = performSse(jobId, "{\"applicationId\":2001}");
        assertTrue(body.contains("event:progress"), "全命中应补合成 progress");
        assertTrue(body.contains("event:result"), "应输出 result");
        assertTrue(body.contains("event:done"), "应输出 done");
        JsonNode result = parseResultData(body);
        assertEquals("COMPANY_LIBRARY", result.path("generationMode").asText());
        assertEquals(4, result.path("questions").size());
        for (JsonNode q : result.path("questions")) {
            assertEquals("COMPANY_LIBRARY", q.path("sourceType").asText());
        }
        verify(baibaoxiangAgentClient, never()).streamInterview(anyString(), anyString(), any(), any());
    }

    /** 部分命中：BASIC+PROJECT 企业题，其余 AI 补 → MIXED、恰好 4 题、4 题型顺序 */
    @Test
    void partialHit_mixed() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "PROJECT", "ACTIVE");
        mockContext(2002L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2002}");
        JsonNode result = parseResultData(body);
        assertEquals("MIXED", result.path("generationMode").asText());
        JsonNode questions = result.path("questions");
        assertEquals(4, questions.size());
        assertEquals("BASIC", questions.get(0).path("type").asText());
        assertEquals("PROJECT", questions.get(1).path("type").asText());
        assertEquals("BOUNDARY", questions.get(2).path("type").asText());
        assertEquals("COMPREHENSIVE", questions.get(3).path("type").asText());
        assertEquals("COMPANY_LIBRARY", questions.get(0).path("sourceType").asText());
        assertEquals("COMPANY_LIBRARY", questions.get(1).path("sourceType").asText());
        assertEquals("AI_GENERATED", questions.get(2).path("sourceType").asText());
        assertEquals("AI_GENERATED", questions.get(3).path("sourceType").asText());
        // 补题（BOUNDARY/COMPREHENSIVE）content 非提示词模板/占位
        for (int i = 2; i < questions.size(); i++) {
            String c = questions.get(i).path("content").asText("");
            assertFalse(c.contains("请围绕"), "补题 content 不得含提示词模板: " + c);
            assertFalse(c.contains("回答一道高质量面试题"), "补题 content 不得为模板回显: " + c);
            assertFalse("考察要点".equals(questions.get(i).path("keyPoints").asText("")), "keyPoints 不得为占位");
            assertFalse("参考答案".equals(questions.get(i).path("referenceAnswer").asText("")), "referenceAnswer 不得为占位");
        }
    }

    /** MIXED 缺 3 题（真实百宝箱联调场景回归）：BASIC 企业题命中，AI 补 PROJECT/BOUNDARY/COMPREHENSIVE → result + done */
    @Test
    void mixed_basicHit_aiFillsThree_resultDone() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        mockContext(2013L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        // 真实百宝箱流语义（脱敏）：HEADER → CONTENT(3 题 HARD 补题) → COMPLETE（done 结束帧）
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-2013"));
            consumer.accept(AgentStreamEvent.content(aiThreeHardJson()));
            consumer.accept(AgentStreamEvent.complete("req-2013"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());

        String body = performSse(jobId, "{\"applicationId\":2013,\"difficulty\":\"HARD\"}");
        assertTrue(body.contains("event:result"), "MIXED 应输出 result");
        assertTrue(body.contains("event:done"), "应输出 done");
        assertFalse(body.contains("event:error"), "不应输出 error");
        JsonNode result = parseResultData(body);
        assertEquals("MIXED", result.path("generationMode").asText());
        JsonNode questions = result.path("questions");
        assertEquals(4, questions.size(), "企业 BASIC + AI 3 题 = 4 题");
        assertEquals("BASIC", questions.get(0).path("type").asText(), "第 1 题应为企业 BASIC");
        assertEquals("COMPANY_LIBRARY", questions.get(0).path("sourceType").asText(), "BASIC 应为企业题");
        Set<String> aiTypes = new HashSet<>();
        for (int i = 1; i < 4; i++) {
            assertEquals("AI_GENERATED", questions.get(i).path("sourceType").asText());
            assertEquals("HARD", questions.get(i).path("difficulty").asText());
            aiTypes.add(questions.get(i).path("type").asText());
        }
        assertEquals(new HashSet<>(Arrays.asList("PROJECT", "BOUNDARY", "COMPREHENSIVE")), aiTypes,
                "AI 补题应恰为缺失 3 题型");
    }

    /** 空库 → 纯 AI 4 题，无 COMPANY_LIBRARY */
    @Test
    void emptyLibrary_pureAi() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(2003L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2003}");
        JsonNode result = parseResultData(body);
        assertEquals("AI_GENERATED", result.path("generationMode").asText());
        assertEquals(4, result.path("questions").size());
        for (JsonNode q : result.path("questions")) {
            assertEquals("AI_GENERATED", q.path("sourceType").asText());
            assertFalse(q.path("content").asText("").contains("请围绕"), "纯 AI 题 content 不得含提示词模板");
        }
    }

    // ==================== 失败与降级 ====================

    /** 部分命中时 AI 越界（输出 4 题而非缺失 2 题）→ 重试后 2302 */
    @Test
    void aiOutOfRange_retry_2302() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "PROJECT", "ACTIVE");
        mockContext(2004L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-2004"));
            consumer.accept(AgentStreamEvent.content(ai4QuestionsJson()));
            consumer.accept(AgentStreamEvent.complete("req-2004"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());

        String body = performSse(jobId, "{\"applicationId\":2004}");
        assertTrue(body.contains("event:error"), "AI 越界应输出 error");
        assertTrue(body.contains("2302"), "应为 2302");
        assertFalse(body.contains("event:done"), "error 与 done 互斥");
    }

    /** 纯 AI 收紧：AI 返回 3 题（非 4 标准题型）→ 重试后 2302（行为变更回归） */
    @Test
    void pureAi3Questions_2302() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(2009L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-2009"));
            consumer.accept(AgentStreamEvent.content(ai3QuestionsJson()));
            consumer.accept(AgentStreamEvent.complete("req-2009"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());

        String body = performSse(jobId, "{\"applicationId\":2009}");
        assertTrue(body.contains("event:error"), "3 题应校验失败输出 error");
        assertTrue(body.contains("2302"), "应为 2302");
    }

    /** 题库查询异常 → 降级纯 AI，仍正常出 4 题 */
    @Test
    void mapperException_degradeToAi() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        mockContext(2007L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();
        doThrow(new RuntimeException("db down"))
                .when(jobQuestionMapper).selectForPrivateQuestion(any(QuestionSearchQuery.class));
        try {
            String body = performSse(jobId, "{\"applicationId\":2007}");
            JsonNode result = parseResultData(body);
            assertEquals("AI_GENERATED", result.path("generationMode").asText());
            assertEquals(4, result.path("questions").size());
        } finally {
            Mockito.reset(jobQuestionMapper);
        }
    }

    /** 客户端回显模板提示词（模拟旧 Mock / 真实 AI 异常）→ 校验拦截 → 重试后 2302，且调用两次 streamInterview */
    @Test
    void aiTemplateEcho_rejected_2302() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "PROJECT", "ACTIVE");
        mockContext(2102L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-2102"));
            consumer.accept(AgentStreamEvent.content(templateEchoJson()));
            consumer.accept(AgentStreamEvent.complete("req-2102"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());

        String body = performSse(jobId, "{\"applicationId\":2102}");
        assertTrue(body.contains("event:error"), "模板回显应被校验拦截输出 error");
        assertTrue(body.contains("2302"), "应为 2302");
        assertFalse(body.contains("event:done"), "error 与 done 互斥");
        verify(baibaoxiangAgentClient, times(2)).streamInterview(anyString(), anyString(), any(), any());
    }

    // ==================== 安全与隔离 ====================

    /** 企业题不泄露：prompt 不含企业题 content/referenceAnswer */
    @Test
    void privateQuestionNotLeaked() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE", "私有题专属内容-BASIC", "私有题专属答案-BASIC");
        insertQuestion(TEST_COMPANY_ID, "PROJECT", "ACTIVE", "私有题专属内容-PROJECT", "私有题专属答案-PROJECT");
        mockContext(2005L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2005}");
        assertTrue(body.contains("event:result"), "部分命中应正常出题");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(baibaoxiangAgentClient).streamInterview(captor.capture(), anyString(), any(), any());
        String prompt = captor.getValue();
        assertFalse(prompt.contains("私有题专属内容"), "prompt 不应含企业题 content");
        assertFalse(prompt.contains("私有题专属答案"), "prompt 不应含企业题 referenceAnswer");
        assertTrue(prompt.contains("【本次出题控制】"), "prompt 应含本次出题控制块");
    }

    /** 跨企业隔离：企业 B 的题不进企业 A；企业 A 无题 → 纯 AI */
    @Test
    void crossCompanyIsolation() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(OTHER_COMPANY_ID, "BASIC", "ACTIVE");
        mockContext(2006L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2006}");
        JsonNode result = parseResultData(body);
        assertEquals("AI_GENERATED", result.path("generationMode").asText(), "企业 B 的题不应进入企业 A");
        for (JsonNode q : result.path("questions")) {
            assertEquals("AI_GENERATED", q.path("sourceType").asText());
        }
    }

    /** 坏私有题（content 空但题型标准）→ 丢弃、其题型交 AI 补 */
    @Test
    void badPrivateQuestion_dropped() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "BOUNDARY", "ACTIVE", "", "坏题答案"); // content 空 → 坏题
        mockContext(2008L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2008}");
        JsonNode result = parseResultData(body);
        assertEquals("MIXED", result.path("generationMode").asText());
        JsonNode questions = result.path("questions");
        assertEquals(4, questions.size());
        assertEquals("COMPANY_LIBRARY", questions.get(0).path("sourceType").asText(), "BASIC 应为企业题");
        assertFalse(body.contains("坏题答案"), "坏题内容不应进入 result");
    }

    /** 命中题 evaluationDimensions 数组含 name/weight */
    @Test
    void companyLibrary_evaluationDimensions() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        mockContext(2011L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2011}");
        JsonNode q = parseResultData(body).path("questions").get(0);
        assertTrue(q.path("evaluationDimensions").isArray());
        assertTrue(q.path("evaluationDimensions").size() > 0, "命中题应含评分维度");
        assertTrue(q.path("evaluationDimensions").get(0).has("name"));
        assertTrue(q.path("evaluationDimensions").get(0).has("weight"));
    }

    /** Mapper 层回归：selectForPrivateQuestion 仅返回标准 4 题型；selectForAgentSearch 未被改动仍可用 */
    @Test
    void agentSearchUnchanged_bypassPrivateFilter() {
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        insertQuestion(TEST_COMPANY_ID, "XXX_EXTRA", "ACTIVE"); // 非标准题型

        // 带 skillTags（走技能重合度排序分支，规避 selectForAgentSearch 空技能时的既有窗口函数位置指示缺陷）
        QuestionSearchQuery q = new QuestionSearchQuery();
        q.setCompanyId(TEST_COMPANY_ID);
        q.setDifficulty("MEDIUM");
        q.setSkillTags(Collections.singletonList("Java"));
        List<JobQuestion> privateList = jobQuestionMapper.selectForPrivateQuestion(q);
        assertNotNull(privateList);
        for (JobQuestion jq : privateList) {
            assertTrue(Arrays.asList("BASIC", "PROJECT", "BOUNDARY", "COMPREHENSIVE").contains(jq.getQuestionType()),
                    "私有题查询应过滤非标准题型，实际: " + jq.getQuestionType());
        }
        // selectForAgentSearch 不抛异常（Tool3 语义未变）
        assertNotNull(jobQuestionMapper.selectForAgentSearch(q));
    }

    /** 出题衔接回归：INACTIVE/REJECTED/软删题不被确定性选题命中（阶段6.1 写侧状态保证） */
    @Test
    void privateHit_excludeInactiveRejectedSoftDeleted() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "INACTIVE");
        insertQuestion(TEST_COMPANY_ID, "PROJECT", "REJECTED");
        insertQuestion(TEST_COMPANY_ID, "BOUNDARY", "ACTIVE");
        jdbcTemplate.update("UPDATE job_question SET deleted_at = NOW() WHERE company_id = ? AND question_type = 'BOUNDARY' AND deleted_at IS NULL",
                TEST_COMPANY_ID);
        mockContext(2012L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2012}");
        JsonNode result = parseResultData(body);
        assertEquals("AI_GENERATED", result.path("generationMode").asText(),
                "非 ACTIVE/软删题不应被确定性选题命中，应降级纯 AI");
        for (JsonNode q : result.path("questions")) {
            assertEquals("AI_GENERATED", q.path("sourceType").asText());
        }
    }

    // ==================== 辅助方法 ====================

    /** 执行 SSE 请求并返回响应体（等待异步完成） */
    private String performSse(long jobId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/hr/jobs/{jobId}/interview-questions/generate", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
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

    /** 解析 SSE body 中 event:result 后的 data JSON */
    private JsonNode parseResultData(String body) {
        Matcher m = Pattern.compile("event:result\\s*\\Rdata:(.+?)(?:\\R\\R|\\z)", Pattern.DOTALL).matcher(body);
        assertTrue(m.find(), "未找到 result data, body=" + body);
        try {
            return objectMapper.readTree(m.group(1).trim());
        } catch (Exception e) {
            throw new RuntimeException("解析 result data 失败", e);
        }
    }

    /**
     * 合法补题结果 stub 助手：按 prompt「缺失题型」行返回对应题型；无该行（空库/题库异常降级纯 AI）
     * 返回完整 4 题（防纯 AI 用例题数不足误报 2302，isValidResult 纯 AI 要求恰好 4 题）
     */
    private void stubValidAi() {
        doAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            List<String> missing = extractRequiredTypesFromPrompt(prompt);
            String json = missing.isEmpty() ? ai4QuestionsJson() : aiQuestionsFor(missing);
            consumer.accept(AgentStreamEvent.header("req-stub"));
            consumer.accept(AgentStreamEvent.content(json));
            consumer.accept(AgentStreamEvent.complete("req-stub"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());
    }

    /** 解析 prompt「requiredTypes：」行 → 题型列表（无该行返回空） */
    private List<String> extractRequiredTypesFromPrompt(String prompt) {
        List<String> result = new ArrayList<>();
        for (String line : prompt.split("\\R")) {
            int idx = line.indexOf("requiredTypes：");
            if (idx >= 0) {
                for (String t : line.substring(idx + "requiredTypes：".length()).split(",")) {
                    String type = t.trim();
                    if (!type.isEmpty()) {
                        result.add(type);
                    }
                }
                break;
            }
        }
        return result;
    }

    /** 按缺失题型生成真实题 JSON（无缺失 → 完整 4 题；有 → 对应题型各 1 题，content 真实非模板） */
    private String aiQuestionsFor(List<String> types) {
        StringBuilder sb = new StringBuilder("{\"requestId\":\"req\",\"questions\":[");
        List<String> list = types.isEmpty() ? Arrays.asList("BASIC", "PROJECT", "BOUNDARY", "COMPREHENSIVE") : types;
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String t = list.get(i);
            sb.append("{\"type\":\"").append(t).append("\",\"difficulty\":\"MEDIUM\",\"content\":\"考察")
                    .append(t).append("能力的真实模拟面试题\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Mock ResumeFeignClient（投递详情）+ ResumeDetailFeignClient（简历卡片） */
    private void mockContext(Long appId, Long jobId, Long companyId, String status, BigDecimal dc) {
        InternalApplicationDTO app = new InternalApplicationDTO();
        app.setId(appId);
        app.setJobId(jobId);
        app.setCompanyId(companyId);
        app.setCandidateId(1001L);
        app.setResumeId(1L);
        app.setStatus(status);
        when(resumeFeignClient.getApplication(appId)).thenReturn(Result.success(app));
        when(resumeDetailFeignClient.getResumeDetail(1L))
                .thenReturn(Result.success(resumeDetail(dc)));
    }

    /** 简历卡片：points 填充率映射 dataCompleteness */
    private ResumeDetailDTO resumeDetail(BigDecimal dc) {
        ResumeDetailDTO rd = new ResumeDetailDTO();
        rd.setId(1L);
        rd.setParseStatus("PARSED");
        Map<String, Object> card = new LinkedHashMap<>();
        List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "项目经历");
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(Collections.singletonMap("text", "负责 Java 后端开发"));
        if (dc != null && dc.compareTo(new BigDecimal("0.5")) >= 0) {
            points.add(Collections.singletonMap("text", "主导高并发系统设计"));
        }
        section.put("points", points);
        sections.add(section);
        card.put("sections", sections);
        rd.setCardStructure(card);
        return rd;
    }

    private HttpHeaders hrHeaders(long companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "HR");
        headers.set("X-Company-Id", String.valueOf(companyId));
        return headers;
    }

    /** 插入岗位 + 画像（coreSkills 含 Java） */
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

    /** 插入 ACTIVE 标准私有题 */
    private void insertQuestion(long companyId, String type, String status) {
        insertQuestion(companyId, type, status, "私有题专属内容-" + type, "私有题专属答案-" + type);
    }

    /** 插入私有题（content/referenceAnswer 可定制，用于坏题/泄露用例） */
    private void insertQuestion(long companyId, String type, String status, String content, String refAnswer) {
        String evaluation = "[{\"name\":\"技术深度\",\"weight\":0.6},{\"name\":\"表达\",\"weight\":0.4}]";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_question (company_id, job_type, skill_tags, question_type, difficulty, content, "
                            + "content_sha256, key_points, reference_answer, evaluation_points, source, status, version, created_by) "
                            + "VALUES (?, 'JAVA_BACKEND', '[\"Java\"]', ?, 'MEDIUM', ?, ?, ?, ?, ?, 'HR_CREATED', ?, 1, 1)");
            ps.setLong(1, companyId);
            ps.setString(2, type);
            ps.setString(3, content);
            ps.setString(4, sha256(content));
            ps.setString(5, content);
            ps.setString(6, refAnswer);
            ps.setString(7, evaluation);
            ps.setString(8, status);
            return ps;
        });
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** AI 越界输出：4 个标准题型（部分命中时不应输出 4 题） */
    private String ai4QuestionsJson() {
        return "{\"requestId\":\"req\",\"questions\":["
                + "{\"type\":\"BASIC\",\"difficulty\":\"MEDIUM\",\"content\":\"c1\"},"
                + "{\"type\":\"PROJECT\",\"difficulty\":\"MEDIUM\",\"content\":\"c2\"},"
                + "{\"type\":\"BOUNDARY\",\"difficulty\":\"MEDIUM\",\"content\":\"c3\"},"
                + "{\"type\":\"COMPREHENSIVE\",\"difficulty\":\"MEDIUM\",\"content\":\"c4\"}"
                + "]}";
    }

    /** 真实百宝箱补题 result（脱敏）：缺失 3 题型 PROJECT/BOUNDARY/COMPREHENSIVE，HARD */
    private String aiThreeHardJson() {
        return "{\"requestId\":\"req\",\"questions\":["
                + "{\"type\":\"PROJECT\",\"difficulty\":\"HARD\",\"content\":\"新用户首单转化策略题（脱敏）\"},"
                + "{\"type\":\"BOUNDARY\",\"difficulty\":\"HARD\",\"content\":\"增长归因权衡题（脱敏）\"},"
                + "{\"type\":\"COMPREHENSIVE\",\"difficulty\":\"HARD\",\"content\":\"用户增长三阶段策略题（脱敏）\"}"
                + "]}";
    }

    /** AI 输出 3 题（纯 AI 收紧回归：应恰好 4 个标准题型） */
    private String ai3QuestionsJson() {
        return "{\"requestId\":\"req\",\"questions\":["
                + "{\"type\":\"BASIC\",\"difficulty\":\"MEDIUM\",\"content\":\"c1\"},"
                + "{\"type\":\"PROJECT\",\"difficulty\":\"MEDIUM\",\"content\":\"c2\"},"
                + "{\"type\":\"BOUNDARY\",\"difficulty\":\"MEDIUM\",\"content\":\"c3\"}"
                + "]}";
    }

    /** 模拟模板回显题（BOUNDARY/COMPREHENSIVE 缺失 → 返回提示词模板，模拟旧 Mock/异常） */
    private String templateEchoJson() {
        return "{\"requestId\":\"req\",\"questions\":["
                + "{\"type\":\"BOUNDARY\",\"difficulty\":\"MEDIUM\",\"content\":\"请围绕「BOUNDARY」题型回答一道高质量面试题。\",\"keyPoints\":\"考察要点\",\"referenceAnswer\":\"参考答案\"},"
                + "{\"type\":\"COMPREHENSIVE\",\"difficulty\":\"MEDIUM\",\"content\":\"请围绕「COMPREHENSIVE」题型回答一道高质量面试题。\",\"keyPoints\":\"考察要点\",\"referenceAnswer\":\"参考答案\"}"
                + "]}";
    }

    // ==================== 本次出题控制块（Prompt 协议回归） ====================

    /** MIXED：BASIC 命中缺 3 题 → Prompt 含本次出题控制块（questionCount=3 / requiredTypes 缺失 3 题型），首轮合法只调一次 */
    @Test
    void mixed_basicHit_promptControlBlock_andSingleCall() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        mockContext(2021L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2021,\"difficulty\":\"HARD\"}");
        assertTrue(body.contains("event:done"), "首轮合法输出应 done");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(baibaoxiangAgentClient, times(1)).streamInterview(captor.capture(), anyString(), any(), any());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("【本次出题控制】"), "Prompt 应含本次出题控制块");
        assertTrue(prompt.contains("questionCount：3"), "MIXED 应 questionCount=3，实际: " + prompt);
        assertTrue(prompt.contains("requiredTypes：PROJECT,BOUNDARY,COMPREHENSIVE"),
                "MIXED 应 requiredTypes=缺失 3 题型，实际: " + prompt);
        assertTrue(prompt.contains("difficulty：HARD"), "应含 difficulty=HARD");
    }

    /** 纯 AI：无企业题 → Prompt 含 questionCount=4 与 4 标准题型，首轮合法只调一次 */
    @Test
    void pureAi_promptControlBlock_andSingleCall() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        mockContext(2022L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        stubValidAi();

        String body = performSse(jobId, "{\"applicationId\":2022}");
        assertTrue(body.contains("event:done"), "首轮合法输出应 done");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(baibaoxiangAgentClient, times(1)).streamInterview(captor.capture(), anyString(), any(), any());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("questionCount：4"), "纯 AI 应 questionCount=4，实际: " + prompt);
        assertTrue(prompt.contains("requiredTypes：BASIC,PROJECT,BOUNDARY,COMPREHENSIVE"),
                "纯 AI 应 4 标准题型，实际: " + prompt);
    }

    /** 校验失败日志：输出 expected/actual 计数与题型，不记录题目正文 */
    @Test
    void validationFailure_logsExpectedActual_withoutContent() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        insertQuestion(TEST_COMPANY_ID, "BASIC", "ACTIVE");
        mockContext(2023L, jobId, TEST_COMPANY_ID, "SCREENED", new BigDecimal("0.90"));
        // 期望 3 题（PROJECT/BOUNDARY/COMPREHENSIVE），stub 只返回 2 题 → 校验失败 → 重试仍失败 → 2302
        doAnswer(invocation -> {
            Consumer<AgentStreamEvent> consumer = invocation.getArgument(3);
            consumer.accept(AgentStreamEvent.header("req-2023"));
            consumer.accept(AgentStreamEvent.content("{\"questions\":["
                    + "{\"type\":\"PROJECT\",\"difficulty\":\"HARD\",\"content\":\"项目题正文\"},"
                    + "{\"type\":\"BOUNDARY\",\"difficulty\":\"HARD\",\"content\":\"边界题正文\"}"
                    + "]}"));
            consumer.accept(AgentStreamEvent.complete("req-2023"));
            return null;
        }).when(baibaoxiangAgentClient).streamInterview(anyString(), anyString(), any(), any());

        ListAppender<ILoggingEvent> appender = attachLogAppender(InterviewAgentServiceImpl.class);
        try {
            String body = performSse(jobId, "{\"applicationId\":2023,\"difficulty\":\"HARD\"}");
            assertTrue(body.contains("2302"), "两次校验失败应输出 2302");
            boolean found = appender.list.stream().anyMatch(e -> {
                String msg = e.getFormattedMessage();
                return msg.contains("expectedCount=3")
                        && msg.contains("expectedTypes=PROJECT,BOUNDARY,COMPREHENSIVE")
                        && msg.contains("actualCount=2")
                        && msg.contains("actualTypes=PROJECT,BOUNDARY");
            });
            assertTrue(found, "校验失败日志应含 expected/actual 明细");
            boolean leaked = appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("项目题正文"));
            assertFalse(leaked, "日志不得含题目正文");
        } finally {
            detachLogAppender(appender);
        }
    }

    // ==================== 日志捕获辅助 ====================

    /** 附加 ListAppender 到目标类 logger，收集日志事件 */
    private ListAppender<ILoggingEvent> attachLogAppender(Class<?> clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /** 移除并停止 ListAppender */
    private void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        appender.stop();
        Logger logger = (Logger) LoggerFactory.getLogger(InterviewAgentServiceImpl.class);
        logger.detachAppender(appender);
    }
}

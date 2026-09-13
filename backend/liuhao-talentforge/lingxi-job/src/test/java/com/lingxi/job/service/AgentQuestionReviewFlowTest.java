package com.lingxi.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.job.domain.dto.query.QuestionSearchQuery;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.mapper.JobQuestionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 面试官申请入库 → HR 审核 → Agent 检索 闭环集成测试（阶段6.3 二期）
 * <p>真实连接本地 MySQL，走 HTTP 全链路：INTERVIEWER submit（AI_GENERATED+PENDING_REVIEW）
 * → HR review（APPROVE→ACTIVE / REJECT→REJECTED+review_reason）→ JobQuestionMapper
 * selectForPrivateQuestion 验证 ACTIVE 才命中、PENDING_REVIEW/REJECTED 不命中；pending-count 递增。
 * 不改任何生产代码——审核/检索均为既有实现，本测试产出闭环验证。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class AgentQuestionReviewFlowTest {

    /** 测试专用企业ID（高位独占） */
    private static final long TEST_COMPANY_ID = 999901L;

    /** 面试官（申请入库）与 HR（审核）均为测试独占高位 ID */
    private static final long INTERVIEWER_ID = 99990003L;
    private static final long HR_ID = 99990002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobQuestionMapper jobQuestionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 本测试实际创建的岗位ID，cleanup 按精确 ID 清理 */
    private final List<Long> createdJobIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long jobId : createdJobIds) {
            jdbcTemplate.update("DELETE FROM job_profile WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_post WHERE id = ?", jobId);
        }
        // 提交产生的题目按 created_by=面试官独占高位ID + 测试企业精确清理
        jdbcTemplate.update("DELETE FROM job_question WHERE created_by = ? AND company_id = ?",
                INTERVIEWER_ID, TEST_COMPANY_ID);
        createdJobIds.clear();
    }

    /** 闭环①：提交后（PENDING_REVIEW）不被 Agent 题库检索命中 */
    @Test
    void pendingNotHitByAgentSearch() throws Exception {
        Long jobId = insertJob();
        Long qid = submitToLibrary(jobId, "PENDING 不应命中题");

        List<Long> hit = searchIds();
        assertFalse(hit.contains(qid), "PENDING_REVIEW 题目不应被 Agent 检索命中");
    }

    /** 闭环②：提交 → HR 审核 APPROVE → ACTIVE → Agent 检索命中 */
    @Test
    void approveThenAgentHits() throws Exception {
        Long jobId = insertJob();
        Long qid = submitToLibrary(jobId, "审核通过后应命中题");

        review(qid, "APPROVE", null);

        assertEquals("ACTIVE", statusOf(qid), "审核通过后应为 ACTIVE");
        List<Long> hit = searchIds();
        assertTrue(hit.contains(qid), "ACTIVE 题目应被 Agent 检索命中");
    }

    /** 闭环③：提交 → HR 审核 REJECT + reason → REJECTED + review_reason 落库 → 不被命中 */
    @Test
    void rejectRecordsReasonAndNotHit() throws Exception {
        Long jobId = insertJob();
        Long qid = submitToLibrary(jobId, "审核拒绝应记录原因且不命中");

        review(qid, "REJECT", "题干与岗位要求不符");

        assertEquals("REJECTED", statusOf(qid), "审核拒绝后应为 REJECTED");
        assertEquals("题干与岗位要求不符", reviewReasonOf(qid), "拒绝原因应落库");
        List<Long> hit = searchIds();
        assertFalse(hit.contains(qid), "REJECTED 题目不应被 Agent 检索命中");
    }

    /** 闭环④：提交后 pending-count 递增 1 */
    @Test
    void pendingCountIncrementedAfterSubmit() throws Exception {
        Long jobId = insertJob();
        long before = jobQuestionMapper.countPending(TEST_COMPANY_ID);
        submitToLibrary(jobId, "待审核计数应递增");
        long after = jobQuestionMapper.countPending(TEST_COMPANY_ID);
        assertEquals(before + 1, after, "提交后待审核计数应 +1");
    }

    /** 闭环⑤：非 PENDING_REVIEW 状态不可审核 → 2406（既有语义） */
    @Test
    void reviewRequiresPendingState() throws Exception {
        Long jobId = insertJob();
        Long qid = submitToLibrary(jobId, "非待审核不可再审核");
        review(qid, "APPROVE", null);   // → ACTIVE

        // 对已 ACTIVE 题再审核 → 2406
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", qid)
                        .headers(hrHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"version\":" + versionOf(qid) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2406));
    }

    // ==================== 辅助方法 ====================

    /** 面试官提交一道题，返回入库题目ID */
    private Long submitToLibrary(Long jobId, String content) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"BASIC\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp);
        return node.path("data").path("questionId").asLong();
    }

    /** HR 审核一道题（APPROVE / REJECT + reason） */
    private void review(Long questionId, String action, String reason) throws Exception {
        String body = reason == null
                ? "{\"action\":\"" + action + "\",\"version\":" + versionOf(questionId) + "}"
                : "{\"action\":\"" + action + "\",\"version\":" + versionOf(questionId)
                        + ",\"reason\":\"" + reason + "\"}";
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", questionId)
                        .headers(hrHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** selectForPrivateQuestion 按测试企业/IT/MEDIUM 检索命中的题目ID列表 */
    private List<Long> searchIds() {
        QuestionSearchQuery q = new QuestionSearchQuery();
        q.setCompanyId(TEST_COMPANY_ID);
        q.setJobType("IT");
        q.setDifficulty("MEDIUM");
        return jobQuestionMapper.selectForPrivateQuestion(q).stream()
                .map(JobQuestion::getId)
                .collect(Collectors.toList());
    }

    private String statusOf(Long questionId) {
        return jdbcTemplate.queryForObject("SELECT status FROM job_question WHERE id=?", String.class, questionId);
    }

    private String reviewReasonOf(Long questionId) {
        return jdbcTemplate.queryForObject("SELECT review_reason FROM job_question WHERE id=?",
                String.class, questionId);
    }

    private Integer versionOf(Long questionId) {
        return jdbcTemplate.queryForObject("SELECT version FROM job_question WHERE id=?",
                Integer.class, questionId);
    }

    private HttpHeaders interviewerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(INTERVIEWER_ID));
        headers.set("X-User-Role", "INTERVIEWER");
        headers.set("X-Company-Id", String.valueOf(TEST_COMPANY_ID));
        return headers;
    }

    private HttpHeaders hrHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(HR_ID));
        headers.set("X-User-Role", "HR");
        headers.set("X-Company-Id", String.valueOf(TEST_COMPANY_ID));
        return headers;
    }

    /** 插入测试岗位 + 画像（IT），返回岗位ID */
    private Long insertJob() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, salary_raw_text, total_hc, jd_text, status, published_at, "
                            + "deleted_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, TEST_COMPANY_ID);
            ps.setString(2, "【TEST】Java后端工程师");
            ps.setString(3, "IT");
            ps.setString(4, "IT");
            ps.setString(5, "110000");
            ps.setString(6, "北京");
            ps.setInt(7, 2);
            ps.setString(8, "BACHELOR");
            ps.setLong(9, 100000L);
            ps.setLong(10, 200000L);
            ps.setString(11, "CNY");
            ps.setString(12, "MONTH");
            ps.setInt(13, 12);
            ps.setInt(14, 0);
            ps.setString(15, "10k-20k");
            ps.setInt(16, 2);
            ps.setString(17, "负责后端服务开发与维护");
            ps.setString(18, "PUBLISHED");
            ps.setTimestamp(19, Timestamp.valueOf(LocalDateTime.now()));
            ps.setNull(20, Types.TIMESTAMP);
            ps.setLong(21, INTERVIEWER_ID);
            return ps;
        }, keyHolder);
        Long jobId = keyHolder.getKey().longValue();
        createdJobIds.add(jobId);
        jdbcTemplate.update("INSERT INTO job_profile (company_id, job_id, job_type) VALUES (?, ?, ?)",
                TEST_COMPANY_ID, jobId, "IT");
        return jobId;
    }
}

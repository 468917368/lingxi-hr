package com.lingxi.job.controller;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 面试官提交 AI 题目申请入库接口测试（阶段6.3 二期）
 * <p>真实连接本地 MySQL，前置插入已发布岗位（含画像）。cleanup 按 insertJob() 记录的精确
 * jobId 清理画像/岗位，并按 created_by=面试官独占高位 ID 清理提交产生的 job_question。
 * 覆盖角色矩阵（INTERVIEWER 放行 / HR+CANDIDATE 403）、企业隔离（2101）、参数校验（400）、
 * 未登录（401）、画像缺失（400）、落库强制 source/status。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class InterviewerQuestionControllerTest {

    /** 测试专用企业ID（高位独占，避开 666880/888880/777777/777778 等既有测试企业） */
    private static final long TEST_COMPANY_ID = 999901L;

    /** 跨企业隔离验证用企业ID */
    private static final long OTHER_COMPANY_ID = 999902L;

    /** 面试官（测试专用独占高位ID，created_by 用它清理 job_question，不误伤业务数据） */
    private static final long INTERVIEWER_ID = 99990003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试实际创建的岗位ID，cleanup 按精确 ID 清理 */
    private final List<Long> createdJobIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long jobId : createdJobIds) {
            jdbcTemplate.update("DELETE FROM job_profile WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_post WHERE id = ?", jobId);
        }
        // 提交产生的题目按 created_by=面试官独占高位ID + 测试企业精确清理
        jdbcTemplate.update("DELETE FROM job_question WHERE created_by = ? AND company_id IN (?, ?)",
                INTERVIEWER_ID, TEST_COMPANY_ID, OTHER_COMPANY_ID);
        createdJobIds.clear();
    }

    /** 用例1：面试官对本企业 AI 题申请成功 → HTTP 200 + code 200 + PENDING_REVIEW，落库强制 source/status */
    @Test
    void submit_success_returns200AndPendingReview() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BASIC", "请解释 HashMap 扩容机制")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.questionId").exists());
        Integer saved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_question WHERE company_id=? AND job_type='IT' "
                        + "AND source='AI_GENERATED' AND status='PENDING_REVIEW' AND created_by=?",
                Integer.class, TEST_COMPANY_ID, INTERVIEWER_ID);
        assertEquals(1, saved, "提交后应落库 1 条 AI_GENERATED+PENDING_REVIEW 题目");
    }

    /** 用例5：CANDIDATE 角色提交 → code=403 */
    @Test
    void submit_candidateRole_returns403() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(candidateHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BASIC", "题目内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 用例5：HR 角色提交 → code=403（HR 不能走面试官申请入库接口，其建题走 /api/v1/hr/questions） */
    @Test
    void submit_hrRole_returns403() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(hrHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BASIC", "题目内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 用例6：跨企业岗位提交 → code=2101（企业隔离） */
    @Test
    void submit_crossCompanyJob_returns2101() throws Exception {
        Long otherJobId = insertJob(OTHER_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", otherJobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BASIC", "题目内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /** 参数校验：content 为空 → HTTP 400 + code 400（@NotBlank） */
    @Test
    void submit_contentBlank_returns400() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"BASIC\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 参数校验：questionType 非法 → code=400（QuestionValidator） */
    @Test
    void submit_questionTypeInvalid_returns400() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("WRONG_TYPE", "题目内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 未登录 → code=401（类级 @RequireLogin） */
    @Test
    void submit_noLogin_returns401() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BASIC", "题目内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 岗位无画像（无法归档 job_type）→ code=400 */
    @Test
    void submit_profileMissing_returns400() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, false);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BASIC", "题目内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 测试矩阵 #9（HTTP 层）：请求 JSON 多传 source/status/companyId/createdBy 控制字段
     * → 未知字段被 Jackson 忽略（默认 FAIL_ON_UNKNOWN_PROPERTIES=false），落库仍为后端真值
     * （AI_GENERATED / PENDING_REVIEW / 当前企业 / 当前面试官）。
     */
    @Test
    void submit_forgedFieldsIgnored_http() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);
        String forged = "{\"questionType\":\"BASIC\",\"content\":\"伪造字段HTTP验证\","
                + "\"source\":\"HR_CREATED\",\"status\":\"ACTIVE\","
                + "\"companyId\":999999,\"createdBy\":888}";

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
        Integer forgedCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_question WHERE company_id=? AND job_type='IT' "
                        + "AND source='AI_GENERATED' AND status='PENDING_REVIEW' AND created_by=?",
                Integer.class, TEST_COMPANY_ID, INTERVIEWER_ID);
        assertEquals(1, forgedCnt, "多传控制字段应被忽略，落库仍为后端真值");
    }

    /** keyPoints 传 JSON 数组（真实百宝箱 SSE 形态）→ HTTP 200 + code 200，落库按换行拼接为单字符串 */
    @Test
    void submit_keyPointsArray_joinedIntoSingleString() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);
        String body = "{\"questionType\":\"BASIC\",\"content\":\"HashMap 扩容机制\","
                + "\"keyPoints\":[\"并发安全\",\"哈希冲突\",\"扩容时机\"]}";

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
        String saved = jdbcTemplate.queryForObject(
                "SELECT key_points FROM job_question WHERE company_id=? AND job_type='IT' "
                        + "AND source='AI_GENERATED' AND created_by=? ORDER BY id DESC LIMIT 1",
                String.class, TEST_COMPANY_ID, INTERVIEWER_ID);
        assertEquals("并发安全\n哈希冲突\n扩容时机", saved, "数组 keyPoints 应按换行拼接后落库");
    }

    /** keyPoints 传单字符串（兼容 Mock/历史形态）→ HTTP 200，按单条要点原样落库 */
    @Test
    void submit_keyPointsString_compatible() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);
        String body = "{\"questionType\":\"BASIC\",\"content\":\"HashMap 扩容机制\","
                + "\"keyPoints\":\"并发安全与哈希冲突\"}";

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String saved = jdbcTemplate.queryForObject(
                "SELECT key_points FROM job_question WHERE company_id=? AND job_type='IT' "
                        + "AND source='AI_GENERATED' AND created_by=? ORDER BY id DESC LIMIT 1",
                String.class, TEST_COMPANY_ID, INTERVIEWER_ID);
        assertEquals("并发安全与哈希冲突", saved, "字符串 keyPoints 应按单条要点原样落库");
    }

    /** 请求体非法 JSON（截断）→ HTTP 400 + code 400（JobAgentExceptionHandler 接管，不再 500） */
    @Test
    void submit_malformedJson_returns400() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, true);

        mockMvc.perform(post("/api/v1/hr/jobs/{id}/interview-questions/submit-to-library", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionType\":\"BASIC\",\"content\":\"题目内容\",\"keyPoints\":["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 辅助方法 ====================

    private HttpHeaders interviewerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(INTERVIEWER_ID));
        headers.set("X-User-Role", "INTERVIEWER");
        headers.set("X-Company-Id", String.valueOf(TEST_COMPANY_ID));
        return headers;
    }

    private HttpHeaders hrHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "99990002");
        headers.set("X-User-Role", "HR");
        headers.set("X-Company-Id", String.valueOf(TEST_COMPANY_ID));
        return headers;
    }

    private HttpHeaders candidateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "99990001");
        headers.set("X-User-Role", "CANDIDATE");
        headers.set("X-Company-Id", String.valueOf(TEST_COMPANY_ID));
        return headers;
    }

    private static String body(String questionType, String content) {
        return "{\"questionType\":\"" + questionType + "\",\"content\":\"" + content + "\"}";
    }

    /**
     * 插入测试岗位（默认 IT/北京），withProfile=true 时一并插入 job_profile（job_type=IT）
     *
     * @return 自增岗位ID（cleanup 按此精确清理）
     */
    private Long insertJob(long companyId, boolean withProfile) {
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
            ps.setLong(1, companyId);
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
        if (withProfile) {
            jdbcTemplate.update(
                    "INSERT INTO job_profile (company_id, job_id, job_type) VALUES (?, ?, ?)",
                    companyId, jobId, "IT");
        }
        return jobId;
    }
}

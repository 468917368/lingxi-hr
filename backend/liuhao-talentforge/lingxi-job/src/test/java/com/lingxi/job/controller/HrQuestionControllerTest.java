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
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HR 企业私有题库管理接口测试（阶段6.1）
 * <p>真实连接本地 MySQL，前置插入 job_question（version=0），AfterEach 清理。
 * 企业 A=666880、企业 B=888880 双企业验证跨企业越权。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class HrQuestionControllerTest {

    /** 测试专用企业 A（本测试主要操作方） */
    private static final long COMPANY_A = 666880L;

    /** 测试专用企业 B（越权访问方） */
    private static final long COMPANY_B = 888880L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
    }

    // ==================== 新增 ====================

    @Test
    void create_active() throws Exception {
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-基础-JVM", "BASIC", "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.source").value("HR_CREATED"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void create_duplicate_2404() throws Exception {
        insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-重复内容");
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-重复内容", "BASIC", "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2404));
    }

    @Test
    void create_crossCompanyDuplicate_ok() throws Exception {
        insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-跨企业相同");
        // 企业 B 同 content → 允许（uk 含 company_id）
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-跨企业相同", "BASIC", "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void create_afterSoftDelete_reuse() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-删除后重建");
        mockMvc.perform(delete("/api/v1/hr/questions/{id}", id).param("version", "0")
                        .headers(hrHeaders(COMPANY_A)))
                .andExpect(jsonPath("$.code").value(200));
        // 软删后重建同 content → 恢复复用（200，行数不增）
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-删除后重建", "BASIC", "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_question WHERE company_id=? AND content_sha256=SHA2(?,256)",
                Integer.class, COMPANY_A, "题-删除后重建");
        assertIntegerEquals(1, cnt, "软删行应被复用而非新增");
    }

    @Test
    void create_invalidType_400() throws Exception {
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-非法题型", "XXX", "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_invalidDifficulty_400() throws Exception {
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-非法难度", "BASIC", "ILLEGAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_emptyContent_400() throws Exception {
        // 空/空白 content 被 @NotBlank 在 @Valid 层拦截 → HTTP 400（对齐 detail_nonNumericId 模式）
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("  ", "BASIC", "MEDIUM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_invalidSkillTags_400() throws Exception {
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                tags.append(",");
            }
            tags.append("\"t").append(i).append("\"");
        }
        String body = "{\"jobType\":\"JAVA_BACKEND\",\"questionType\":\"BASIC\",\"content\":\"题-技能过多\","
                + "\"skillTags\":[" + tags + "]}";
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_invalidEvaluation_400() throws Exception {
        String body = "{\"jobType\":\"JAVA_BACKEND\",\"questionType\":\"BASIC\",\"content\":\"题-评分非法\","
                + "\"evaluationPoints\":[{\"name\":\"深度\",\"weight\":1.5}]}";
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_emptyEvaluationPoints_normalizedToNull() throws Exception {
        // 空数组等价"无评分要点"→ 存 NULL，VO 输出空数组
        String body = "{\"jobType\":\"JAVA_BACKEND\",\"questionType\":\"BASIC\",\"content\":\"题-评分空数组\","
                + "\"evaluationPoints\":[]}";
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.evaluationPoints").isArray())
                .andExpect(jsonPath("$.data.evaluationPoints.length()").value(0));
    }

    @Test
    void create_skillTagsEmptyItem_400() throws Exception {
        String body = "{\"jobType\":\"JAVA_BACKEND\",\"questionType\":\"BASIC\",\"content\":\"题-技能空项\","
                + "\"skillTags\":[\"Java\",\" \"]}";
        mockMvc.perform(post("/api/v1/hr/questions")
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 列表/详情 ====================

    @Test
    void list_pageFilter() throws Exception {
        insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-筛选A");
        insertQuestion(COMPANY_A, "PROJECT", "ACTIVE", "题-筛选B");
        insertQuestion(COMPANY_A, "BOUNDARY", "INACTIVE", "题-筛选C");

        // 题型 + 状态筛选 + id DESC
        mockMvc.perform(get("/api/v1/hr/questions")
                        .param("questionType", "BASIC").param("status", "ACTIVE")
                        .headers(hrHeaders(COMPANY_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
        // 难度筛选非法 → 400
        mockMvc.perform(get("/api/v1/hr/questions").param("difficulty", "XXX")
                        .headers(hrHeaders(COMPANY_A)))
                .andExpect(jsonPath("$.code").value(400));
        // 未传 difficulty → 不过滤（返回全部 3 条）
        mockMvc.perform(get("/api/v1/hr/questions").param("size", "50")
                        .headers(hrHeaders(COMPANY_A)))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void list_sensitiveFieldsHidden() throws Exception {
        insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-列表脱敏");
        mockMvc.perform(get("/api/v1/hr/questions").headers(hrHeaders(COMPANY_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].referenceAnswer").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].keyPoints").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].evaluationPoints").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].content").exists());
    }

    @Test
    void detail_full() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-详情");
        mockMvc.perform(get("/api/v1/hr/questions/{id}", id).headers(hrHeaders(COMPANY_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.referenceAnswer").value("参考答案"))
                .andExpect(jsonPath("$.data.keyPoints").exists())
                .andExpect(jsonPath("$.data.evaluationPoints[0].name").value("技术深度"))
                .andExpect(jsonPath("$.data.reviewedBy").isEmpty());
    }

    // ==================== 编辑 ====================

    @Test
    void update_ok() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-编辑前");
        mockMvc.perform(put("/api/v1/hr/questions/{id}", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-编辑后", "PROJECT", "HARD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.questionType").value("PROJECT"))
                .andExpect(jsonPath("$.data.difficulty").value("HARD"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void update_rejectedToPending() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "REJECTED", "题-重编回审");
        jdbcTemplate.update("UPDATE job_question SET reviewed_by=2, reviewed_at=NOW(), review_reason='先拒绝' WHERE id=?", id);
        mockMvc.perform(put("/api/v1/hr/questions/{id}", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-重编回审", "BASIC", "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.reviewedBy").isEmpty())
                .andExpect(jsonPath("$.data.reviewReason").isEmpty());
    }

    @Test
    void update_versionConflict_2402() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-版本冲突");
        mockMvc.perform(put("/api/v1/hr/questions/{id}", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-版本冲突", "BASIC", "MEDIUM").replace("\"version\":0", "\"version\":9")))
                .andExpect(jsonPath("$.code").value(2402));
    }

    @Test
    void update_duplicateContent_2404() throws Exception {
        insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-撞已存在");
        Long id = insertQuestion(COMPANY_A, "PROJECT", "ACTIVE", "题-待编辑");
        mockMvc.perform(put("/api/v1/hr/questions/{id}", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-撞已存在", "PROJECT", "MEDIUM")))
                .andExpect(jsonPath("$.code").value(2404));
    }

    // ==================== 删除/启停 ====================

    @Test
    void delete_soft() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-删除");
        mockMvc.perform(delete("/api/v1/hr/questions/{id}", id).param("version", "0")
                        .headers(hrHeaders(COMPANY_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 详情不可见 → 2401
        mockMvc.perform(get("/api/v1/hr/questions/{id}", id).headers(hrHeaders(COMPANY_A)))
                .andExpect(jsonPath("$.code").value(2401));
        // 列表不含
        mockMvc.perform(get("/api/v1/hr/questions").headers(hrHeaders(COMPANY_A)))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }

    @Test
    void delete_missingVersion_400() throws Exception {
        // 契约：删除必传 version（乐观锁），漏传 → 400（与其他写操作一致）
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-删除缺版本");
        // 漏传 version：@RequestParam required=true → HTTP 400 + Result code=400
        mockMvc.perform(delete("/api/v1/hr/questions/{id}", id).headers(hrHeaders(COMPANY_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 未删除（参数绑定失败，未进 Service），带正确 version 仍可删除
        mockMvc.perform(delete("/api/v1/hr/questions/{id}", id).param("version", "0")
                        .headers(hrHeaders(COMPANY_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void status_toggle() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-启停");
        // DISABLE → INACTIVE
        mockMvc.perform(patch("/api/v1/hr/questions/{id}/status", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLE\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
        // ENABLE → ACTIVE
        mockMvc.perform(patch("/api/v1/hr/questions/{id}/status", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLE\",\"version\":1}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        // 对 ACTIVE 再 DISABLE 后非法（已 ACTIVE 时 DISABLE 是合法；此处测 REJECTED 不可启停）
    }

    @Test
    void status_illegalState_2403() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "REJECTED", "题-非法启停");
        mockMvc.perform(patch("/api/v1/hr/questions/{id}/status", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLE\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(2403));
    }

    // ==================== 审核 ====================

    @Test
    void review_approve() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "PENDING_REVIEW", "题-审核通过");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reviewedBy").isNotEmpty());
    }

    @Test
    void review_reject() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "PENDING_REVIEW", "题-审核拒绝");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"reason\":\"内容不符\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewReason").value("内容不符"));
    }

    @Test
    void review_illegalState_2406() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-非法审核");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(2406));
    }

    @Test
    void review_rejectWithoutReason_2406() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "PENDING_REVIEW", "题-拒绝缺原因");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(2406));
    }

    @Test
    void update_activeKeepsReview() throws Exception {
        // ACTIVE 题曾有审核记录：编辑后应保留（仅 REJECTED 重编回 PENDING_REVIEW 才清）
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-编辑保留审核");
        jdbcTemplate.update("UPDATE job_question SET reviewed_by=2, reviewed_at=NOW(), review_reason='通过' WHERE id=?", id);
        mockMvc.perform(put("/api/v1/hr/questions/{id}", id)
                        .headers(hrHeaders(COMPANY_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-编辑保留审核", "BASIC", "MEDIUM")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reviewedBy").value(2))
                .andExpect(jsonPath("$.data.reviewReason").value("通过"));
    }

    @Test
    void pendingCount() throws Exception {
        insertQuestion(COMPANY_A, "BASIC", "PENDING_REVIEW", "题-待审1");
        insertQuestion(COMPANY_A, "PROJECT", "PENDING_REVIEW", "题-待审2");
        insertQuestion(COMPANY_A, "BOUNDARY", "ACTIVE", "题-已启用");
        mockMvc.perform(get("/api/v1/hr/questions/pending-count").headers(hrHeaders(COMPANY_A)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));
    }

    // ==================== 跨企业越权 ====================

    @Test
    void crossCompany_read_2401() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-越权读");
        mockMvc.perform(get("/api/v1/hr/questions/{id}", id).headers(hrHeaders(COMPANY_B)))
                .andExpect(jsonPath("$.code").value(2401));
    }

    @Test
    void crossCompany_write_2401() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "ACTIVE", "题-越权写");
        mockMvc.perform(put("/api/v1/hr/questions/{id}", id)
                        .headers(hrHeaders(COMPANY_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题-越权写", "BASIC", "MEDIUM")))
                .andExpect(jsonPath("$.code").value(2401));
        mockMvc.perform(delete("/api/v1/hr/questions/{id}", id).param("version", "0")
                        .headers(hrHeaders(COMPANY_B)))
                .andExpect(jsonPath("$.code").value(2401));
    }

    @Test
    void crossCompany_statusAndReview_2401() throws Exception {
        Long id = insertQuestion(COMPANY_A, "BASIC", "PENDING_REVIEW", "题-越权审核");
        mockMvc.perform(patch("/api/v1/hr/questions/{id}/status", id)
                        .headers(hrHeaders(COMPANY_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLE\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(2401));
        mockMvc.perform(post("/api/v1/hr/questions/{id}/review", id)
                        .headers(hrHeaders(COMPANY_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(2401));
    }

    // ==================== 辅助方法 ====================

    /** HR 请求头（X-User-Id/X-User-Role/X-Company-Id） */
    private HttpHeaders hrHeaders(long companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "HR");
        headers.set("X-Company-Id", String.valueOf(companyId));
        return headers;
    }

    /** 新增题目请求体（version=0，默认 BASIC/MEDIUM） */
    private String questionJson(String content, String questionType, String difficulty) {
        return "{\"jobType\":\"JAVA_BACKEND\",\"questionType\":\"" + questionType + "\",\"difficulty\":\""
                + difficulty + "\",\"content\":\"" + content + "\",\"skillTags\":[\"Java\"],"
                + "\"keyPoints\":\"考察要点\",\"referenceAnswer\":\"参考答案\","
                + "\"evaluationPoints\":[{\"name\":\"技术深度\",\"weight\":0.6}],\"version\":0}";
    }

    /** SQL 预插题目（version=0，便于后续操作传 version） */
    private Long insertQuestion(long companyId, String questionType, String status, String content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_question (company_id, job_type, skill_tags, question_type, difficulty, "
                            + "content, content_sha256, key_points, reference_answer, evaluation_points, "
                            + "source, status, version, created_by) "
                            + "VALUES (?, 'JAVA_BACKEND', '[\"Java\"]', ?, 'MEDIUM', ?, SHA2(?,256), ?, ?, ?, "
                            + "'HR_CREATED', ?, 0, 1001)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, questionType);
            ps.setString(3, content);
            ps.setString(4, content);
            ps.setString(5, "考察要点");
            ps.setString(6, "参考答案");
            ps.setString(7, "[{\"name\":\"技术深度\",\"weight\":0.6}]");
            ps.setString(8, status);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void assertIntegerEquals(int expected, Integer actual, String msg) {
        if (actual == null || actual != expected) {
            throw new AssertionError(msg + "，期望 " + expected + " 实际 " + actual);
        }
    }
}

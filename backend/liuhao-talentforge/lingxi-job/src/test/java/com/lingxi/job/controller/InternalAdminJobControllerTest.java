package com.lingxi.job.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部管理员接口测试（统计 + 违规下架岗位）
 * <p>仿 HrJobControllerTest：JdbcTemplate 插入测试岗位、AfterEach 清理；内部接口走服务身份头。
 * offline 用例覆盖成功/幂等/版本冲突/状态机/参数校验/操作人边界/权限矩阵/version 读取。</p>
 *
 * @author lingxi-team
 * @since 2026-08-04
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "service-auth.tokens.lingxi-user=user-token-dev",
        "service-auth.tokens.lingxi-resume=resume-token-dev",
        "service-auth.tokens.lingxi-hr=hr-token-dev",
        "service-auth.tokens.lingxi-admin=admin-token-dev"
})
class InternalAdminJobControllerTest {

    /** 测试专用企业ID */
    private static final long TEST_COMPANY_ID = 999999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 每次测试后清理测试企业插入的数据 */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
    }

    // ==================== 原统计用例 ====================

    /**
     * 岗位总数统计 → code=200 + data.value 存在
     */
    @Test
    void statisticsCount_returnsOk() throws Exception {
        mockMvc.perform(get("/internal/jobs/statistics/count")
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.value").exists());
    }

    // ==================== offline 成功 ====================

    /**
     * PUBLISHED 岗位违规下架 → 200，status=CLOSED、closeReason=VIOLATION、version 1，DB 落库
     */
    @Test
    void offline_success() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closeReason").value("VIOLATION"))
                .andExpect(jsonPath("$.data.version").value(1));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason, version FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", row.get("status"));
        assertEquals("VIOLATION", row.get("close_reason"));
        assertEquals(1, ((Number) row.get("version")).intValue());
    }

    /**
     * PAUSED 岗位 + X-Operator-Id 操作人 → 200，status=CLOSED、closeReason=VIOLATION
     */
    @Test
    void offline_success_withOperator() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PAUSED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closeReason").value("VIOLATION"));
    }

    // ==================== 幂等 ====================

    /**
     * CLOSED+VIOLATION 重复下架（version 一致）→ 幂等成功，version 不 +1，DB 不变
     */
    @Test
    void offline_idempotent() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "CLOSED", "VIOLATION");
        jdbcTemplate.update("UPDATE job_post SET version = 3 WHERE id = ?", jobId);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.version").value(3));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT version FROM job_post WHERE id = ?", jobId);
        assertEquals(3, ((Number) row.get("version")).intValue());
    }

    /**
     * CLOSED+VIOLATION 重复下架（version 错误 999）→ 幂等成功且不校验 version（返回当前 DB 版本）
     */
    @Test
    void offline_idempotent_versionIgnored() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "CLOSED", "VIOLATION");
        jdbcTemplate.update("UPDATE job_post SET version = 3 WHERE id = ?", jobId);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    // ==================== 不存在 / 软删除 ====================

    /**
     * 岗位不存在 → 2101
     */
    @Test
    void offline_notFound() throws Exception {
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", 999999999L)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /**
     * 软删除岗位 → selectById 视为不存在 → 2101
     */
    @Test
    void offline_deletedJob() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        jdbcTemplate.update("UPDATE job_post SET deleted_at = NOW() WHERE id = ?", jobId);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    // ==================== 版本冲突 ====================

    /**
     * PUBLISHED version=0，请求 version=5 → 2102，DB 不变
     */
    @Test
    void offline_versionConflict() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2102));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, version FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", row.get("status"));
        assertEquals(0, ((Number) row.get("version")).intValue());
    }

    // ==================== 状态机 ====================

    /**
     * DRAFT 直接下架 → 2103（不允许下架草稿）
     */
    @Test
    void offline_draftRejected() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "DRAFT", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2103));
    }

    /**
     * CLOSED+MANUAL 下架 → 2103（仅同原因 VIOLATION 幂等，其他原因非法）
     */
    @Test
    void offline_closedOtherReason() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "CLOSED", "MANUAL");
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2103));
    }

    // ==================== 参数校验 ====================

    /**
     * reason 非法（非 VIOLATION）→ Service 400
     */
    @Test
    void offline_invalidReason() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("MANUAL", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * reason 未知枚举值 → Service 400
     */
    @Test
    void offline_badEnumReason() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("FOO", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * reason 缺失 → HTTP400 + code 400（@NotBlank）
     */
    @Test
    void offline_reasonMissing() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody(null, "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * reason 超长（33 字符）→ HTTP400 + code 400（@Size）
     */
    @Test
    void offline_reasonTooLong() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            sb.append('R');
        }
        String longReason = sb.toString();
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody(longReason, "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * remark 缺失 → HTTP400 + code 400（@NotBlank）
     */
    @Test
    void offline_remarkMissing() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", null, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * remark 超长（501 字符）→ HTTP400 + code 400（@Size）
     */
    @Test
    void offline_remarkTooLong() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append('R');
        }
        String longRemark = sb.toString();
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", longRemark, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * version 缺失 → HTTP400 + code 400（@NotNull）
     */
    @Test
    void offline_versionMissing() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 认证 / 权限 ====================

    /**
     * 无服务身份头 → HTTP401 + code 2001
     */
    @Test
    void offline_authMissing() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }

    /**
     * lingxi-hr 调 admin offline 路径 → HTTP403 + code 2002（权限矩阵正则验证）
     */
    @Test
    void offline_noPermission() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
    }

    // ==================== version 读取（阻断1 补全） ====================

    /**
     * 管理员分页列表返回 version（selectAdminList 新增 p.version）
     */
    @Test
    void listAdminJobs_returnsVersion() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(get("/internal/admin/jobs")
                        .param("companyId", String.valueOf(TEST_COMPANY_ID))
                        .param("page", "1")
                        .param("size", "20")
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].version").value(0));
    }

    /**
     * 管理员详情返回 version（getAdminJobDetail setVersion）
     */
    @Test
    void getAdminJobDetail_returnsVersion() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(get("/internal/admin/jobs/{jobId}", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    /**
     * 集成：先查详情取 version → 用该 version 调 offline → 成功
     */
    @Test
    void offline_usesVersionFromDetail() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        MvcResult detail = mockMvc.perform(get("/internal/admin/jobs/{jobId}", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").exists())
                .andReturn();
        JsonNode node = objectMapper.readTree(detail.getResponse().getContentAsString());
        int versionFromDetail = node.path("data").path("version").asInt();
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", versionFromDetail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    // ==================== remark 消毒 / 操作人边界 ====================

    /**
     * remark 含 \r\n\t → 下架成功且 DB 状态正确（消毒仅影响日志，不影响结果）
     */
    @Test
    void offline_remarkSanitized() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "第一行\r\n第二行\t制表符", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals("VIOLATION", row.get("close_reason"));
    }

    /**
     * X-Operator-Id 非数字 → HTTP400 + code 400（参数类型不匹配）
     */
    @Test
    void offline_operatorIdNonNumeric() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * X-Operator-Id 负数 → Service 拒绝，code 400（操作人ID非法）
     */
    @Test
    void offline_operatorIdNegative() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null);
        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .header("X-Operator-Id", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offlineBody("VIOLATION", "岗位内容违反平台发布规则", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 私有辅助 ====================

    /**
     * 插入测试岗位（仅 job_post，不插画像；version 用 DB 默认 0）
     */
    private Long insertJob(long companyId, String status, String closeReason) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, total_hc, jd_text, status, close_reason, closed_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "Java后端工程师");
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
            ps.setInt(15, 5);
            ps.setString(16, "负责后端服务开发与维护");
            ps.setString(17, status);
            ps.setString(18, closeReason);
            if ("CLOSED".equals(status)) {
                ps.setTimestamp(19, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                ps.setNull(19, Types.TIMESTAMP);
            }
            ps.setInt(20, 1);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 构造 offline 请求体
     */
    private String offlineBody(String reason, String remark, Integer version) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("remark", remark);
        body.put("version", version);
        return objectMapper.writeValueAsString(body);
    }
}

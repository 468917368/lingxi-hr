package com.lingxi.job.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部岗位接口测试（校验详情 + 搜索 + 企业岗位列表）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "service-auth.tokens.lingxi-user=user-token-dev",
        "service-auth.tokens.lingxi-resume=resume-token-dev",
        "service-auth.tokens.lingxi-hr=hr-token-dev",
        "service-auth.tokens.lingxi-admin=admin-token-dev"
})
class InternalJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本类企业岗位列表测试专用企业 ID（避免与真实数据冲突，AfterEach 清理） */
    private static final long TEST_COMPANY_A = 91001L;
    private static final long TEST_COMPANY_B = 91002L;
    private static final long TEST_EMPTY_COMPANY = 91999L;

    /**
     * 岗位不存在 → code:2101（HTTP 200）
     */
    @Test
    void getJob_NotFound_returns2101() throws Exception {
        mockMvc.perform(get("/internal/jobs/999999")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /**
     * 内部搜索（user 有权限）→ code=200，返回结构正确
     */
    @Test
    void search_returnsOk() throws Exception {
        mockMvc.perform(get("/internal/jobs/search?keyword=HashMap")
                        .header("X-Caller-Service", "lingxi-user")
                        .header("X-Service-Token", "user-token-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    /**
     * 企业岗位列表：只返回目标企业的未删除岗位（软删除排除、跨企业隔离、稳定排序）
     */
    @Test
    void listCompanyJobs_returnsOnlyTargetCompanyNonDeletedJobs() throws Exception {
        Long newestId = insertJob(TEST_COMPANY_A, "PUBLISHED", null, 5, 1, 1,
                LocalDateTime.of(2026, 8, 5, 10, 0));
        insertJob(TEST_COMPANY_A, "CLOSED", null, 2, 0, 1,
                LocalDateTime.of(2026, 8, 4, 10, 0));
        insertJob(TEST_COMPANY_A, "DRAFT", null, 1, 0, 0,
                LocalDateTime.of(2026, 8, 3, 10, 0));
        // 软删除记录发布时间设为最新，证明它即使排在最前也必须被排除
        insertJob(TEST_COMPANY_A, "PUBLISHED", LocalDateTime.now(), 9, 0, 0,
                LocalDateTime.of(2026, 8, 6, 10, 0));
        insertJob(TEST_COMPANY_B, "PUBLISHED", null, 9, 0, 0,
                LocalDateTime.of(2026, 8, 7, 10, 0));

        mockMvc.perform(get("/internal/jobs/company/{companyId}", TEST_COMPANY_A)
                        .headers(internalHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].jobId").value(newestId))
                .andExpect(jsonPath("$.data[0].availableHc").value(3))
                .andExpect(jsonPath("$.data[*].companyId").doesNotExist());
    }

    /**
     * 企业岗位列表：企业无未删除岗位 → 空数组（非错误）
     */
    @Test
    void listCompanyJobs_empty_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/internal/jobs/company/{companyId}", TEST_EMPTY_COMPANY)
                        .headers(internalHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 权限回归：无该路由权限的服务（lingxi-hr）调用企业岗位列表 → 403 + code:2002，
     * 防止未来误把权限矩阵扩宽到不该放行的服务
     */
    @Test
    void listCompanyJobs_hrNoPermission_returns403_2002() throws Exception {
        mockMvc.perform(get("/internal/jobs/company/{companyId}", TEST_COMPANY_A)
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
    }

    /**
     * 每次测试后清理本类插入的测试企业岗位数据
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id IN (?, ?, ?)",
                TEST_COMPANY_A, TEST_COMPANY_B, TEST_EMPTY_COMPANY);
    }

    /**
     * 内部服务认证请求头（成员 A 对应的 lingxi-user 调用方）
     */
    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Caller-Service", "lingxi-user");
        headers.set("X-Service-Token", "user-token-dev");
        return headers;
    }

    /**
     * 插入测试岗位（支持企业隔离/状态/软删除/HC 计数/发布时间），返回自增 jobId
     */
    private Long insertJob(long companyId, String status, LocalDateTime deletedAt,
                           int totalHc, int reservedHc, int confirmedHc, LocalDateTime publishedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_currency, "
                            + "salary_period, salary_months, is_salary_negotiable, total_hc, reserved_hc, "
                            + "confirmed_hc, jd_text, status, published_at, deleted_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "【TESTJOB】企业岗位列表测试岗位");
            ps.setString(3, "IT");
            ps.setString(4, "IT");
            ps.setString(5, "110000");
            ps.setString(6, "北京");
            ps.setInt(7, 1);
            ps.setString(8, "BACHELOR");
            ps.setString(9, "CNY");
            ps.setString(10, "MONTH");
            ps.setInt(11, 12);
            ps.setInt(12, 0);
            ps.setInt(13, totalHc);
            ps.setInt(14, reservedHc);
            ps.setInt(15, confirmedHc);
            ps.setString(16, "负责后端服务开发与维护");
            ps.setString(17, status);
            if (publishedAt == null) {
                ps.setNull(18, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(18, Timestamp.valueOf(publishedAt));
            }
            if (deletedAt == null) {
                ps.setNull(19, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(19, Timestamp.valueOf(deletedAt));
            }
            ps.setInt(20, 1);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}

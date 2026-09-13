package com.lingxi.job.service;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.request.JobOfflineRequest;
import com.lingxi.job.domain.dto.request.JobStatusActionRequest;
import com.lingxi.job.domain.dto.response.JobStatusResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 岗位状态历史与审计收尾测试（阶段7 收尾）
 * <p>真实 MySQL。覆盖：
 * <ul>
 *   <li>publish/close/reopen（HR 操作）与 offline（ADMIN 操作）四类 job_status_log 落库</li>
 *   <li>offline 的 reason_detail=remark 真实入库、request_id 为 null</li>
 *   <li>GET /status-history 分页/倒序/企业隔离(2101)/size 越界收敛</li>
 *   <li>HR 操作缺失操作人 → 400；offline 缺失 X-Operator-Id → 400</li>
 * </ul>
 * Service 直调时用 {@link UserContext#set} 注入 HR 上下文；MockMvc 走 hrHeaders 由 AuthInterceptor 注入。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class JobStatusLogTest {

    /** 测试专用企业ID（高位独占） */
    private static final long TEST_COMPANY_ID = 999700L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HrJobService hrJobService;

    @Autowired
    private InternalAdminJobService internalAdminJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本次创建的岗位ID，cleanup 按精确 ID 清理 job_status_log → job_profile → job_post */
    private final List<Long> createdJobIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        UserContext.clear();
        for (Long jobId : createdJobIds) {
            jdbcTemplate.update("DELETE FROM job_status_log WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_profile WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_post WHERE id = ?", jobId);
        }
        createdJobIds.clear();
    }

    // ==================== 用例 ====================

    /** 发布落库：DRAFT→PUBLISHED / MANUAL_PUBLISH / HR / 操作人=1001 */
    @Test
    void publish_writesLog() {
        setHrContext();
        Long jobId = insertJob("DRAFT", null, true);

        JobStatusResponse resp = hrJobService.changeJobStatus(jobId, statusRequest("PUBLISH", 0, 1));

        assertEquals("PUBLISHED", resp.getStatus());
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId));
        Map<String, Object> log = queryLatestLog(jobId);
        assertEquals("DRAFT", log.get("from_status"));
        assertEquals("PUBLISHED", log.get("to_status"));
        assertEquals("MANUAL_PUBLISH", log.get("reason"));
        assertEquals(1001L, ((Number) log.get("operator_id")).longValue());
        assertEquals("HR", log.get("operator_role"));
    }

    /** 手动关闭落库：PUBLISHED→CLOSED / MANUAL / HR */
    @Test
    void close_writesLog() {
        setHrContext();
        Long jobId = insertJob("PUBLISHED", null, false);

        hrJobService.changeJobStatus(jobId, statusRequest("CLOSE", 0, null));

        Map<String, Object> log = queryLatestLog(jobId);
        assertEquals("PUBLISHED", log.get("from_status"));
        assertEquals("CLOSED", log.get("to_status"));
        assertEquals("MANUAL", log.get("reason"));
        assertEquals(1001L, ((Number) log.get("operator_id")).longValue());
        assertEquals("HR", log.get("operator_role"));
    }

    /** HR 重开落库：CLOSED(HC_CONFIRMED_FULL)→PUBLISHED / REOPEN / HR */
    @Test
    void reopen_writesLog() {
        setHrContext();
        Long jobId = insertJob("CLOSED", "HC_CONFIRMED_FULL", false);

        hrJobService.changeJobStatus(jobId, statusRequest("REOPEN", 0, null));

        Map<String, Object> log = queryLatestLog(jobId);
        assertEquals("CLOSED", log.get("from_status"));
        assertEquals("PUBLISHED", log.get("to_status"));
        assertEquals("REOPEN", log.get("reason"));
        assertEquals(1001L, ((Number) log.get("operator_id")).longValue());
        assertEquals("HR", log.get("operator_role"));
    }

    /** 违规下架落库：PUBLISHED→CLOSED / VIOLATION / ADMIN / reason_detail=remark 真实入库 / request_id=null */
    @Test
    void offline_writesLog_withReasonDetail() {
        Long jobId = insertJob("PUBLISHED", null, false);

        internalAdminJobService.offlineJob(jobId, offlineRequest(0, "VIOLATION", "刷单作弊"), 5L);

        assertEquals("CLOSED", jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId));
        Map<String, Object> log = queryLatestLog(jobId);
        assertEquals("PUBLISHED", log.get("from_status"));
        assertEquals("CLOSED", log.get("to_status"));
        assertEquals("VIOLATION", log.get("reason"));
        assertEquals("刷单作弊", log.get("reason_detail"), "reason_detail 应真实入库（insert 加列生效）");
        assertEquals(5L, ((Number) log.get("operator_id")).longValue());
        assertEquals("ADMIN", log.get("operator_role"));
        assertNull(log.get("request_id"), "request_id 本阶段统一 null");
    }

    /** offline 缺失操作人（operatorId=null）→ 400（不得静默降级为 SYSTEM/0） */
    @Test
    void offline_missingOperator_rejected() {
        Long jobId = insertJob("PUBLISHED", null, false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> internalAdminJobService.offlineJob(jobId, offlineRequest(0, "VIOLATION", null), null));
        assertEquals(400, ex.getCode());
    }

    /** offline 操作人=0（SYSTEM 保留值）→ 400（不得写 operator_role=ADMIN + operator_id=0 的矛盾审计行） */
    @Test
    void offline_zeroOperator_rejected() {
        Long jobId = insertJob("PUBLISHED", null, false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> internalAdminJobService.offlineJob(jobId, offlineRequest(0, "VIOLATION", null), 0L));
        assertEquals(400, ex.getCode());
    }

    /** HTTP 层：offline 缺失 X-Operator-Id → @RequestHeader 绑定失败 → HTTP 400（真实契约层验证） */
    @Test
    void offline_missingOperatorHeader_http400() throws Exception {
        Long jobId = insertJob("PUBLISHED", null, false);

        mockMvc.perform(post("/internal/admin/jobs/{jobId}/offline", jobId)
                        .header("X-Caller-Service", "lingxi-admin")
                        .header("X-Service-Token", "admin-token-dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"reason\":\"VIOLATION\"}"))
                .andExpect(status().isBadRequest());
    }

    /** HR 状态操作 userId=0（SYSTEM 保留值）→ 400 */
    @Test
    void changeStatus_zeroOperator_rejected() {
        UserDTO user = new UserDTO();
        user.setUserId(0L);
        user.setRole("HR");
        user.setCompanyId(TEST_COMPANY_ID);
        UserContext.set(user);
        Long jobId = insertJob("DRAFT", null, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hrJobService.changeJobStatus(jobId, statusRequest("PUBLISH", 0, 1)));
        assertEquals(400, ex.getCode());
    }

    /** status-history：分页 + created_at 倒序 + total */
    @Test
    void statusHistory_paginatedAndDesc() throws Exception {
        Long jobId = insertJob("PUBLISHED", null, false);
        insertLog(jobId, "DRAFT", "PUBLISHED", "MANUAL_PUBLISH", "HR", 1001L);
        insertLog(jobId, "PUBLISHED", "PAUSED", "HC_RESERVED_FULL", "SYSTEM", 0L);
        insertLog(jobId, "PAUSED", "CLOSED", "HC_CONFIRMED_FULL", "SYSTEM", 0L);

        mockMvc.perform(get("/api/v1/hr/jobs/{jobId}/status-history", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].fromStatus").value("PAUSED"))
                .andExpect(jsonPath("$.data.list[0].toStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.list[0].reason").value("HC_CONFIRMED_FULL"))
                .andExpect(jsonPath("$.data.list[0].operatorRole").value("SYSTEM"))
                .andExpect(jsonPath("$.data.list[1].fromStatus").value("PUBLISHED"));
    }

    /** status-history 企业隔离：其他企业访问 → 2101 */
    @Test
    void statusHistory_companyMismatch_2101() throws Exception {
        Long jobId = insertJob("PUBLISHED", null, false);

        mockMvc.perform(get("/api/v1/hr/jobs/{jobId}/status-history", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /** status-history size 越界收敛：999→50、0→1（对齐 listAdminJobs 风格，不抛 400） */
    @Test
    void statusHistory_sizeConverged() throws Exception {
        Long jobId = insertJob("PUBLISHED", null, false);
        insertLog(jobId, "DRAFT", "PUBLISHED", "MANUAL_PUBLISH", "HR", 1001L);

        mockMvc.perform(get("/api/v1/hr/jobs/{jobId}/status-history", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID)).param("size", "999"))
                .andExpect(jsonPath("$.data.pageSize").value(50));
        mockMvc.perform(get("/api/v1/hr/jobs/{jobId}/status-history", jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID)).param("size", "0"))
                .andExpect(jsonPath("$.data.pageSize").value(1));
    }

    /** HR 状态操作缺失 userId → 400（0=SYSTEM 保留语义，不写 operator_id=0 的矛盾审计行） */
    @Test
    void changeStatus_missingOperator_rejected() {
        UserDTO user = new UserDTO();
        user.setRole("HR");
        user.setCompanyId(TEST_COMPANY_ID);
        UserContext.set(user);
        Long jobId = insertJob("DRAFT", null, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hrJobService.changeJobStatus(jobId, statusRequest("PUBLISH", 0, 1)));
        assertEquals(400, ex.getCode());
    }

    // ==================== 辅助方法 ====================

    /** 注入 HR 上下文（Service 直调用：requireCompanyId/getUserId） */
    private void setHrContext() {
        UserDTO user = new UserDTO();
        user.setUserId(1001L);
        user.setRole("HR");
        user.setCompanyId(TEST_COMPANY_ID);
        UserContext.set(user);
    }

    /** HR 请求头（MockMvc status-history 用，AuthInterceptor 注入 UserContext） */
    private HttpHeaders hrHeaders(long companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "HR");
        headers.set("X-Company-Id", String.valueOf(companyId));
        return headers;
    }

    /** 组装状态机请求 */
    private JobStatusActionRequest statusRequest(String action, int version, Integer profileVersion) {
        JobStatusActionRequest req = new JobStatusActionRequest();
        req.setAction(action);
        req.setVersion(version);
        req.setProfileVersion(profileVersion);
        return req;
    }

    /** 组装违规下架请求 */
    private JobOfflineRequest offlineRequest(int version, String reason, String remark) {
        JobOfflineRequest req = new JobOfflineRequest();
        req.setVersion(version);
        req.setReason(reason);
        req.setRemark(remark);
        return req;
    }

    /** 插入测试岗位（totalHc=5、reserved/confirmed=0，status/closeReason 可配；withProfile 时补画像 version=1） */
    private Long insertJob(String status, String closeReason, boolean withProfile) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, city_code, city_name, total_hc, reserved_hc, confirmed_hc, "
                            + "status, close_reason, jd_text, is_salary_negotiable, salary_period, created_by) "
                            + "VALUES (?, ?, ?, ?, 5, 0, 0, ?, ?, ?, 1, 'MONTH', 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, TEST_COMPANY_ID);
            ps.setString(2, "审计测试岗位");
            ps.setString(3, "110000");
            ps.setString(4, "北京");
            ps.setString(5, status);
            ps.setString(6, closeReason);
            ps.setString(7, "负责后端开发");
            return ps;
        }, keyHolder);
        Long jobId = keyHolder.getKey().longValue();
        createdJobIds.add(jobId);
        if (withProfile) {
            jdbcTemplate.update("INSERT INTO job_profile (company_id, job_id, job_type, core_skills, profile_source, version, confirmed_at, created_at, updated_at) "
                            + "VALUES (?, ?, 'JAVA_BACKEND', '[{\"name\":\"Java\"}]', 'MANUAL', 1, NOW(), NOW(), NOW())",
                    TEST_COMPANY_ID, jobId);
        }
        return jobId;
    }

    /** 手工插入一条状态日志（status-history 分页/倒序用例准备） */
    private void insertLog(Long jobId, String from, String to, String reason, String role, long operatorId) {
        jdbcTemplate.update("INSERT INTO job_status_log (company_id, job_id, from_status, to_status, reason, "
                        + "operator_id, operator_role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                TEST_COMPANY_ID, jobId, from, to, reason, operatorId, role);
    }

    /** 查询该岗位最新一条状态日志 */
    private Map<String, Object> queryLatestLog(Long jobId) {
        return jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, reason, reason_detail, operator_id, operator_role, request_id "
                        + "FROM job_status_log WHERE job_id = ? ORDER BY id DESC LIMIT 1", jobId);
    }
}

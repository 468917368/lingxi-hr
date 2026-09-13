package com.lingxi.job.service;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.job.domain.dto.request.HcConfirmRequest;
import com.lingxi.job.domain.dto.request.HcReleaseRequest;
import com.lingxi.job.domain.dto.request.HcReserveRequest;
import com.lingxi.job.domain.dto.request.JobOfflineRequest;
import com.lingxi.job.domain.dto.request.JobStatusActionRequest;
import com.lingxi.job.domain.entity.JobPost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 岗位完整生命周期联调测试（阶段7 状态历史与审计收尾）
 * <p>真实 MySQL，走真实 Service（非 Mock），覆盖完整状态迁移 + 每次状态变更的 job_status_log 审计。
 * 主路径：草稿→发布→预占满暂停→确认满额关闭→释放重开→到期关闭（5 条 SYSTEM/HR 日志）。
 * 违规分支：草稿→发布→违规下架（ADMIN 日志，reason_detail=remark）。</p>
 * <p>Service 直调时用 {@link UserContext#set} 注入 HR 上下文（publish 需要 requireCompanyId/getUserId）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
class JobLifecycleIntegrationTest {

    /** 测试专用企业ID（高位独占） */
    private static final long TEST_COMPANY_ID = 999690L;

    @Autowired
    private HrJobService hrJobService;

    @Autowired
    private InternalJobService internalJobService;

    @Autowired
    private JobExpireCloseService jobExpireCloseService;

    @Autowired
    private InternalAdminJobService internalAdminJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本次创建的岗位ID，cleanup 精确清理 */
    private final List<Long> createdJobIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        UserContext.clear();
        for (Long jobId : createdJobIds) {
            jdbcTemplate.update("DELETE FROM job_status_log WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_hc_reservation WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_profile WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_post WHERE id = ?", jobId);
        }
        createdJobIds.clear();
    }

    /**
     * 完整生命周期主路径：草稿→发布→预占满暂停→确认满额关闭→释放重开→到期关闭
     * <p>每次状态变更断言岗位 status/原因字段 + job_status_log 最新一条（from/to/reason/operator_role）。</p>
     */
    @Test
    void mainLifecycle_fullChain_withAuditLog() {
        setHrContext();
        // ① 草稿（创建不落日志，矩阵无创建行）
        Long jobId = insertJob(1, "DRAFT", null, true);
        assertEquals(0, countLogs(jobId));

        // ② 发布：DRAFT→PUBLISHED / MANUAL_PUBLISH / HR
        hrJobService.changeJobStatus(jobId, statusRequest("PUBLISH", 0, 1));
        assertStatus(jobId, "PUBLISHED", null, null);
        assertLatestLog(jobId, "DRAFT", "PUBLISHED", "MANUAL_PUBLISH", "HR");

        // ③ 预占满：PUBLISHED→PAUSED / HC_RESERVED_FULL / SYSTEM
        HcReserveRequest reserveReq = new HcReserveRequest();
        reserveReq.setCompanyId(TEST_COMPANY_ID);
        reserveReq.setOfferId(700100L);
        reserveReq.setCandidateId(700101L);
        internalJobService.reserveHc(jobId, reserveReq);
        assertStatus(jobId, "PAUSED", "HC_RESERVED_FULL", null);
        assertLatestLog(jobId, "PUBLISHED", "PAUSED", "HC_RESERVED_FULL", "SYSTEM");

        // ④ 确认满额：PAUSED→CLOSED / HC_CONFIRMED_FULL / SYSTEM
        HcConfirmRequest confirmReq = new HcConfirmRequest();
        confirmReq.setCompanyId(TEST_COMPANY_ID);
        confirmReq.setOfferId(700100L);
        internalJobService.confirmHc(jobId, confirmReq);
        assertStatus(jobId, "CLOSED", null, "HC_CONFIRMED_FULL");
        assertLatestLog(jobId, "PAUSED", "CLOSED", "HC_CONFIRMED_FULL", "SYSTEM");

        // ⑤ 释放确认名额 → 自动重开：CLOSED→PUBLISHED / REJECTED / SYSTEM
        HcReleaseRequest releaseReq = new HcReleaseRequest();
        releaseReq.setCompanyId(TEST_COMPANY_ID);
        releaseReq.setOfferId(700100L);
        releaseReq.setReason("REJECTED");
        internalJobService.releaseHc(jobId, releaseReq);
        assertStatus(jobId, "PUBLISHED", null, null);
        assertLatestLog(jobId, "CLOSED", "PUBLISHED", "REJECTED", "SYSTEM");

        // ⑥ 到期关闭：PUBLISHED→CLOSED / EXPIRED / SYSTEM
        jdbcTemplate.update("UPDATE job_post SET expires_at = NOW() - INTERVAL 1 DAY WHERE id = ?", jobId);
        JobPost post = new JobPost();
        post.setId(jobId);
        post.setCompanyId(TEST_COMPANY_ID);
        post.setStatus("PUBLISHED");
        jobExpireCloseService.closeExpiredJob(post);
        assertStatus(jobId, "CLOSED", null, "EXPIRED");
        assertLatestLog(jobId, "PUBLISHED", "CLOSED", "EXPIRED", "SYSTEM");

        // 审计完整性：5 条日志，顺序为 发布/暂停/关闭/重开/到期
        assertEquals(5, countLogs(jobId), "完整主路径应恰有 5 条状态日志");
    }

    /**
     * 违规分支：草稿→发布→违规下架（ADMIN 落库 + reason_detail=remark）
     */
    @Test
    void violationBranch_publishThenOffline() {
        setHrContext();
        Long jobId = insertJob(5, "DRAFT", null, true);

        hrJobService.changeJobStatus(jobId, statusRequest("PUBLISH", 0, 1));
        assertLatestLog(jobId, "DRAFT", "PUBLISHED", "MANUAL_PUBLISH", "HR");

        JobOfflineRequest offlineReq = new JobOfflineRequest();
        offlineReq.setVersion(1);
        offlineReq.setReason("VIOLATION");
        offlineReq.setRemark("发布虚假岗位");
        internalAdminJobService.offlineJob(jobId, offlineReq, 9L);

        assertStatus(jobId, "CLOSED", null, "VIOLATION");
        Map<String, Object> log = queryLatestLog(jobId);
        assertEquals("PUBLISHED", log.get("from_status"));
        assertEquals("CLOSED", log.get("to_status"));
        assertEquals("VIOLATION", log.get("reason"));
        assertEquals("发布虚假岗位", log.get("reason_detail"), "违规备注应入库");
        assertEquals(9L, ((Number) log.get("operator_id")).longValue());
        assertEquals("ADMIN", log.get("operator_role"));
        assertEquals(2, countLogs(jobId), "发布 + 下架 = 2 条日志");
    }

    // ==================== 辅助方法 ====================

    /** 注入 HR 上下文（publish 需要 requireCompanyId/getUserId） */
    private void setHrContext() {
        UserDTO user = new UserDTO();
        user.setUserId(1001L);
        user.setRole("HR");
        user.setCompanyId(TEST_COMPANY_ID);
        UserContext.set(user);
    }

    /** 组装状态机请求 */
    private JobStatusActionRequest statusRequest(String action, int version, Integer profileVersion) {
        JobStatusActionRequest req = new JobStatusActionRequest();
        req.setAction(action);
        req.setVersion(version);
        req.setProfileVersion(profileVersion);
        return req;
    }

    /** 插入测试岗位（totalHc 可配，DRAFT 时补画像供 PUBLISH 校验） */
    private Long insertJob(int totalHc, String status, String closeReason, boolean withProfile) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, city_code, city_name, total_hc, reserved_hc, confirmed_hc, "
                            + "status, close_reason, jd_text, is_salary_negotiable, salary_period, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, ?, 1, 'MONTH', 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, TEST_COMPANY_ID);
            ps.setString(2, "生命周期联调岗位");
            ps.setString(3, "110000");
            ps.setString(4, "北京");
            ps.setInt(5, totalHc);
            ps.setString(6, status);
            ps.setString(7, closeReason);
            ps.setString(8, "负责后端开发");
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

    /** 断言岗位状态与原因字段 */
    private void assertStatus(Long jobId, String status, String pauseReason, String closeReason) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, pause_reason, close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals(status, row.get("status"));
        assertEquals(pauseReason, row.get("pause_reason"));
        assertEquals(closeReason, row.get("close_reason"));
    }

    /** 断言最新一条状态日志（from/to/reason/operator_role） */
    private void assertLatestLog(Long jobId, String from, String to, String reason, String role) {
        Map<String, Object> log = queryLatestLog(jobId);
        assertEquals(from, log.get("from_status"));
        assertEquals(to, log.get("to_status"));
        assertEquals(reason, log.get("reason"));
        assertEquals(role, log.get("operator_role"));
    }

    /** 该岗位状态日志条数 */
    private int countLogs(Long jobId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_status_log WHERE job_id = ?", Integer.class, jobId);
        return c == null ? 0 : c;
    }

    /** 该岗位最新一条状态日志 */
    private Map<String, Object> queryLatestLog(Long jobId) {
        return jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, reason, reason_detail, operator_id, operator_role "
                        + "FROM job_status_log WHERE job_id = ? ORDER BY id DESC LIMIT 1", jobId);
    }
}

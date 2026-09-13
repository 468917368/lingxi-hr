package com.lingxi.job.scheduler;

import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobStatusLog;
import com.lingxi.job.mapper.JobStatusLogMapper;
import com.lingxi.job.service.JobExpireCloseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 岗位到期自动关闭定时任务测试（阶段7 状态自动管理）
 * <p>真实连接本地 MySQL，JdbcTemplate 造数（含 expires_at/status/close_reason/deleted_at），
 * 直接调用 {@link JobExpireCloseScheduler#closeExpiredJobs()} 或 {@link JobExpireCloseService}。
 * @SpyBean JobStatusLogMapper：默认真实落库断言审计；用例 8 内 stub 返回 0 模拟日志插入失败回滚。
 * 覆盖：过期 PUBLISHED/PAUSED 关闭、未过期/expires_at NULL/软删不动、已 CLOSED 不覆盖原因、
 * 幂等、审计日志事务回滚、fromStatus 并发变化不写错误日志。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
class JobExpireCloseSchedulerTest {

    /** 测试专用企业ID（高位独占） */
    private static final long TEST_COMPANY_ID = 999903L;

    @Autowired
    private JobExpireCloseScheduler scheduler;

    @Autowired
    private JobExpireCloseService jobExpireCloseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private JobStatusLogMapper jobStatusLogMapper;

    /** 本次创建的岗位ID，cleanup 按精确 ID 清理 job_post 与其 job_status_log */
    private final List<Long> createdJobIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long jobId : createdJobIds) {
            jdbcTemplate.update("DELETE FROM job_status_log WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_post WHERE id = ?", jobId);
        }
        createdJobIds.clear();
    }

    /** 用例1：过期 PUBLISHED → CLOSED + EXPIRED + closed_at + version+1，落审计日志（from=PUBLISHED/to=CLOSED/EXPIRED/SYSTEM/0） */
    @Test
    void expiredPublished_closedWithExpiredAndLog() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", LocalDateTime.now().minusDays(1), null, false);

        scheduler.closeExpiredJobs();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason, closed_at, version FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", row.get("status"));
        assertEquals("EXPIRED", row.get("close_reason"));
        assertNotNull(row.get("closed_at"), "应写入 closed_at");
        assertEquals(1, ((Number) row.get("version")).intValue(), "version 应 +1");

        Map<String, Object> logRow = jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, reason, operator_id, operator_role FROM job_status_log WHERE job_id = ?",
                jobId);
        assertEquals("PUBLISHED", logRow.get("from_status"), "审计 from_status 应为扫描时的真实状态");
        assertEquals("CLOSED", logRow.get("to_status"));
        assertEquals("EXPIRED", logRow.get("reason"));
        assertEquals(0L, ((Number) logRow.get("operator_id")).longValue(), "operator_id 应为 0=SYSTEM");
        assertEquals("SYSTEM", logRow.get("operator_role"));
    }

    /** 用例2：过期 PAUSED → CLOSED */
    @Test
    void expiredPaused_closed() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PAUSED", LocalDateTime.now().minusHours(1), null, false);

        scheduler.closeExpiredJobs();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", row.get("status"));
        assertEquals("EXPIRED", row.get("close_reason"));
    }

    /** 用例3：未过期 → 不变 */
    @Test
    void notExpired_unchanged() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", LocalDateTime.now().plusDays(7), null, false);

        scheduler.closeExpiredJobs();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", row.get("status"));
        assertNull(row.get("close_reason"));
    }

    /** 用例4：expires_at NULL → 不关闭 */
    @Test
    void expiresAtNull_unchanged() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null, null, false);

        scheduler.closeExpiredJobs();

        assertEquals("PUBLISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId));
    }

    /** 用例5：已 CLOSED（VIOLATION/MANUAL）且过期 → close_reason 不覆盖、不落新日志 */
    @Test
    void alreadyClosed_reasonNotOverwritten() {
        Long jobId = insertJob(TEST_COMPANY_ID, "CLOSED", LocalDateTime.now().minusDays(1), "VIOLATION", false);

        scheduler.closeExpiredJobs();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", row.get("status"));
        assertEquals("VIOLATION", row.get("close_reason"), "违规关闭原因不应被 EXPIRED 覆盖");
        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_status_log WHERE job_id = ?", Integer.class, jobId);
        assertEquals(0, logs, "已关闭岗位不应新增到期日志");
    }

    /** 用例6：软删除 + 过期 → 不处理 */
    @Test
    void softDeleted_skipped() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", LocalDateTime.now().minusDays(1), null, true);

        scheduler.closeExpiredJobs();

        assertEquals("PUBLISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId),
                "软删除岗位不应被关闭");
    }

    /** 用例7：幂等——第二次扫描不再关闭、审计日志仅 1 条 */
    @Test
    void idempotent_secondRunNoChange() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", LocalDateTime.now().minusDays(1), null, false);

        scheduler.closeExpiredJobs();
        scheduler.closeExpiredJobs();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, version FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", row.get("status"));
        assertEquals(1, ((Number) row.get("version")).intValue(), "第二次不应再变更");
        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_status_log WHERE job_id = ?", Integer.class, jobId);
        assertEquals(1, logs, "审计日志应仅 1 条");
    }

    /** 用例8：审计日志插入非 1 行 → 抛异常，REQUIRES_NEW 回滚岗位关闭（杜绝"已关闭无审计"） */
    @Test
    void logInsertFailure_rollsBackJobUpdate() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", LocalDateTime.now().minusDays(1), null, false);
        doReturn(0).when(jobStatusLogMapper).insert(any(JobStatusLog.class));

        assertThrows(IllegalStateException.class,
                () -> jobExpireCloseService.closeExpiredJob(postOf(jobId, "PUBLISHED")),
                "日志插入失败应抛异常回滚");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason, closed_at, version FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", row.get("status"), "审计失败应回滚岗位关闭");
        assertNull(row.get("close_reason"));
        assertNull(row.get("closed_at"));
        assertEquals(0, ((Number) row.get("version")).intValue(), "version 不应变化");
    }

    /** 用例9：扫描后状态并发变化（实际 PAUSED、传 fromStatus=PUBLISHED）→ 返回 false，不关闭、不写错误 fromStatus 日志 */
    @Test
    void fromStatusChanged_noWrongLog() {
        Long jobId = insertJob(TEST_COMPANY_ID, "PAUSED", LocalDateTime.now().minusDays(1), null, false);

        boolean result = jobExpireCloseService.closeExpiredJob(postOf(jobId, "PUBLISHED"));

        assertFalse(result, "状态并发变化应返回 false，本轮跳过");
        assertEquals("PAUSED", jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId),
                "岗位不应被关闭");
        verify(jobStatusLogMapper, never()).insert(any(JobStatusLog.class));
    }

    // ==================== 辅助 ====================

    private JobPost postOf(Long jobId, String status) {
        JobPost post = new JobPost();
        post.setId(jobId);
        post.setCompanyId(TEST_COMPANY_ID);
        post.setStatus(status);
        return post;
    }

    /** 插入测试岗位（参数化 status/expires_at/close_reason/deleted_at），返回自增 id（cleanup 精确清理） */
    private Long insertJob(long companyId, String status, LocalDateTime expiresAt, String closeReason, boolean deleted) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, salary_raw_text, total_hc, jd_text, status, expires_at, "
                            + "close_reason, deleted_at, created_by) "
                            + "VALUES (?, ?, 'IT', 'IT', '110000', '北京', 2, 'BACHELOR', 100000, 200000, "
                            + "'CNY', 'MONTH', 12, 0, '10k-20k', 5, '负责后端开发', ?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "【TEST】到期关闭岗位");
            ps.setString(3, status);
            ps.setTimestamp(4, expiresAt == null ? null : Timestamp.valueOf(expiresAt));
            ps.setString(5, closeReason);
            ps.setTimestamp(6, deleted ? Timestamp.valueOf(LocalDateTime.now()) : null);
            return ps;
        }, keyHolder);
        Long jobId = keyHolder.getKey().longValue();
        createdJobIds.add(jobId);
        return jobId;
    }
}

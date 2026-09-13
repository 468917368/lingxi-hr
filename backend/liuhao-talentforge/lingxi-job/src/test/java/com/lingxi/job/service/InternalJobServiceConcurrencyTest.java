package com.lingxi.job.service;

import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.request.HcConfirmRequest;
import com.lingxi.job.domain.dto.request.HcReleaseRequest;
import com.lingxi.job.domain.dto.request.HcReserveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HC 并发专项测试（阶段7 状态自动管理）
 * <p>真实连接本地 MySQL，注入真实 {@link InternalJobService}，验证 reserve/confirm/release
 * 在「SELECT ... FOR UPDATE 行锁 + 乐观锁 version + uk_hc_offer 唯一键」下的并发正确性。
 * 关键设计：
 * <ul>
 *   <li>测试方法<b>不加</b>@Transactional——并发线程各自独立事务，数据必须已提交才可见</li>
 *   <li>ExecutorService + CountDownLatch 同时放行 + Future.get(timeout)；禁止 Thread.sleep 控并发</li>
 *   <li>线程池在 finally 中 shutdownNow，杜绝线程泄漏</li>
 *   <li>对业务异常断言 {@link BusinessException#getCode()}，不满足于"抛了异常"</li>
 *   <li>每个用例独立 companyId/jobId/offerId 区间，@AfterEach 按当前 companyId 清理
 *       job_status_log → job_hc_reservation → job_post</li>
 * </ul>
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
class InternalJobServiceConcurrencyTest {

    /** 并发线程数 */
    private static final int N = 10;
    /** 每个并发任务 Future.get 超时（秒），防止死锁挂死 */
    private static final long FUTURE_TIMEOUT_SECONDS = 30;

    /** 测试专用企业ID基址（高位独占，按用例偏移，避开既有测试 999999） */
    private static final long COMPANY_ID_BASE = 999810L;

    @Autowired
    private InternalJobService internalJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 当前用例使用的企业ID，@AfterEach 按此清理 */
    private long currentCompanyId;

    @AfterEach
    void cleanup() {
        if (currentCompanyId == 0L) {
            return;
        }
        jdbcTemplate.update("DELETE FROM job_status_log WHERE company_id = ?", currentCompanyId);
        jdbcTemplate.update("DELETE FROM job_hc_reservation WHERE company_id = ?", currentCompanyId);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", currentCompanyId);
        currentCompanyId = 0L;
    }

    /**
     * A：10 个线程、10 个不同 offer，并发 reserve 同一 totalHc=1 岗位。
     * 岗位行锁串行化 → 首个成功者 reserved=1、available=0、岗位转 PAUSED(HC_RESERVED_FULL)；
     * 后续线程幂等查询各自 offer=null 后命中「status!=PUBLISHED」→ HC_ILLEGAL_STATE(2204)。
     * 断言：恰好 1 成功、9×2204、reserved=1、confirmed=0、仅 1 流水、仅 1 条 PUBLISHED→PAUSED 日志。
     */
    @Test
    void reserve_concurrentDistinctOffers_onlyOneReserves() throws Exception {
        currentCompanyId = COMPANY_ID_BASE;
        Long jobId = insertJob(currentCompanyId, 1, null);

        List<Outcome> outcomes = runConcurrent(idx -> {
            HcReserveRequest req = new HcReserveRequest();
            req.setCompanyId(currentCompanyId);
            req.setOfferId(811000L + idx);
            req.setCandidateId(911000L + idx);
            return doReserve(jobId, req);
        });

        assertConcurrencyInvariants(outcomes, jobId);

        long success = outcomes.stream().filter(o -> o.success).count();
        long illegalState = outcomes.stream().filter(o -> !o.success && Integer.valueOf(2204).equals(o.code)).count();
        long unexpected = outcomes.stream().filter(o -> o.errorClass != null).count();
        assertEquals(1, success, "恰好 1 个线程应成功预占");
        assertEquals(N - 1, illegalState, "其余线程应因岗位已 PAUSED 抛 HC_ILLEGAL_STATE(2204)");
        assertEquals(0, unexpected, "不应出现未知异常");

        Map<String, Object> job = queryJob(jobId);
        assertEquals(1, ((Number) job.get("reserved_hc")).intValue());
        assertEquals(0, ((Number) job.get("confirmed_hc")).intValue());
        assertEquals("PAUSED", job.get("status"));
        assertEquals("HC_RESERVED_FULL", job.get("pause_reason"));
        assertEquals(1, countReservations(currentCompanyId), "仅 1 条 reservation");
        assertEquals(1, countLogs(currentCompanyId), "仅 1 条 PUBLISHED→PAUSED SYSTEM 日志");

        Map<String, Object> log = queryLatestStatusLog();
        assertEquals("PUBLISHED", log.get("from_status"));
        assertEquals("PAUSED", log.get("to_status"));
        assertEquals("HC_RESERVED_FULL", log.get("reason"));
        assertEquals("SYSTEM", log.get("operator_role"));
    }

    /**
     * B：10 个线程相同 companyId/jobId/offerId/candidateId，并发 reserve 同一 totalHc=1 岗位。
     * 岗位行锁串行 → 首个真实预占，后续线程幂等查询命中已提交流水 → 幂等返回成功。
     * 断言：10 全成功、reserved=1、仅 1 流水、仅 1 暂停日志、无 DuplicateKeyException/负数/重复扣减。
     */
    @Test
    void reserve_concurrentSameOffer_isIdempotent() throws Exception {
        currentCompanyId = COMPANY_ID_BASE + 1;
        Long jobId = insertJob(currentCompanyId, 1, null);
        long offerId = 812000L;
        long candidateId = 912000L;

        List<Outcome> outcomes = runConcurrent(idx -> {
            HcReserveRequest req = new HcReserveRequest();
            req.setCompanyId(currentCompanyId);
            req.setOfferId(offerId);
            req.setCandidateId(candidateId);
            return doReserve(jobId, req);
        });

        assertConcurrencyInvariants(outcomes, jobId);

        assertEquals(N, outcomes.stream().filter(o -> o.success).count(), "所有请求语义均为 RESERVED 成功（1 真实 + 9 幂等）");
        assertEquals(0, outcomes.stream().filter(o -> o.errorClass != null).count(),
                "不得出现未处理 DuplicateKeyException 或未知异常");

        Map<String, Object> job = queryJob(jobId);
        assertEquals(1, ((Number) job.get("reserved_hc")).intValue(), "reserved 只加一次");
        assertEquals(0, ((Number) job.get("confirmed_hc")).intValue());
        assertTrue(((Number) job.get("reserved_hc")).intValue() >= 0, "不得为负数");
        assertEquals(1, countReservations(currentCompanyId), "仅 1 条 reservation（唯一键兜底）");
        assertEquals(1, countLogs(currentCompanyId), "仅 1 条暂停日志");
    }

    /**
     * C：前置 reserve 成功后，10 个线程同时 confirm 同一 offer。
     * 首个真实确认：reserved-1/confirmed+1、岗位 CLOSED(HC_CONFIRMED_FULL)；
     * 后续线程命中「CONFIRMED 幂等」短路，不重复增计数。
     * 断言：10 全成功、confirmed=1、岗位 CLOSED、仅 1 条 PAUSED→CLOSED 日志。
     */
    @Test
    void confirm_concurrentSameOffer_onlyOneCountTransition() throws Exception {
        currentCompanyId = COMPANY_ID_BASE + 2;
        Long jobId = insertJob(currentCompanyId, 1, null);
        long offerId = 813000L;
        long candidateId = 913000L;
        // 前置 reserve（串行准备，已提交）
        reserveForSetup(jobId, offerId, candidateId);

        List<Outcome> outcomes = runConcurrent(idx -> {
            HcConfirmRequest req = new HcConfirmRequest();
            req.setCompanyId(currentCompanyId);
            req.setOfferId(offerId);
            return doConfirm(jobId, req);
        });

        assertConcurrencyInvariants(outcomes, jobId);

        assertEquals(N, outcomes.stream().filter(o -> o.success).count(), "所有请求语义均为 CONFIRMED 成功（1 真实 + 9 幂等）");
        assertEquals(0, outcomes.stream().filter(o -> o.errorClass != null).count());

        Map<String, Object> job = queryJob(jobId);
        assertEquals(0, ((Number) job.get("reserved_hc")).intValue());
        assertEquals(1, ((Number) job.get("confirmed_hc")).intValue(), "confirmed 只 +1");
        assertEquals("CLOSED", job.get("status"));
        assertEquals("HC_CONFIRMED_FULL", job.get("close_reason"));
        assertNotNull(job.get("closed_at"), "满额关闭应写 closed_at");

        assertEquals(1, countReservations(currentCompanyId));
        // 前置 reserve 产生 1 条暂停日志 + confirm 产生 1 条关闭日志
        assertEquals(2, countLogs(currentCompanyId), "应恰有 1 条暂停 + 1 条关闭日志");
        Map<String, Object> log = queryLatestStatusLog();
        assertEquals("PAUSED", log.get("from_status"));
        assertEquals("CLOSED", log.get("to_status"));
        assertEquals("HC_CONFIRMED_FULL", log.get("reason"));
    }

    /**
     * D：前置 reserve→confirm（岗位 CLOSED+HC_CONFIRMED_FULL）后，10 个线程同时 release 同一 offer。
     * 首个真实释放：confirmed-1=0、available 恢复、岗位自动重开 PUBLISHED（三态归零 + published_at=NOW()）；
     * 后续线程命中「RELEASED 幂等」短路，不重复回退。
     * 断言：10 全成功、confirmed=0、岗位 PUBLISHED、关闭字段/pause 均 null、仅 1 条 CLOSED→PUBLISHED(reason=REJECTED) 日志、
     *       恢复后 published_at 晚于关闭前值。
     */
    @Test
    void release_concurrentSameOffer_onlyOneCountRollbackAndReopen() throws Exception {
        currentCompanyId = COMPANY_ID_BASE + 3;
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 1, 10, 0, 0);
        Long jobId = insertJob(currentCompanyId, 1, publishedAt);
        long offerId = 814000L;
        long candidateId = 914000L;
        // 前置 reserve → confirm（岗位 CLOSED+HC_CONFIRMED_FULL，已提交）
        reserveForSetup(jobId, offerId, candidateId);
        confirmForSetup(jobId, offerId);

        List<Outcome> outcomes = runConcurrent(idx -> {
            HcReleaseRequest req = new HcReleaseRequest();
            req.setCompanyId(currentCompanyId);
            req.setOfferId(offerId);
            req.setReason("REJECTED");
            return doRelease(jobId, req);
        });

        assertConcurrencyInvariants(outcomes, jobId);

        assertEquals(N, outcomes.stream().filter(o -> o.success).count(), "所有请求语义均为 RELEASED 成功（1 真实 + 9 幂等）");
        assertEquals(0, outcomes.stream().filter(o -> o.errorClass != null).count());

        Map<String, Object> job = queryJob(jobId);
        assertEquals(0, ((Number) job.get("reserved_hc")).intValue());
        assertEquals(0, ((Number) job.get("confirmed_hc")).intValue(), "confirmed 只回退一次，不得为负");
        assertEquals("PUBLISHED", job.get("status"), "满额关闭后释放确认名额应自动恢复 PUBLISHED");
        assertNull(job.get("pause_reason"));
        assertNull(job.get("close_reason"));
        assertNull(job.get("closed_at"));
        LocalDateTime dbPublishedAt = (LocalDateTime) job.get("published_at");
        assertTrue(dbPublishedAt.isAfter(publishedAt), "恢复后 published_at 应晚于关闭前值");

        // 前置 reserve(暂停) + confirm(关闭) + release(重开) = 3 条日志
        assertEquals(3, countLogs(currentCompanyId), "应恰有 暂停+关闭+重开 3 条日志");
        Map<String, Object> log = queryLatestStatusLog();
        assertEquals("CLOSED", log.get("from_status"));
        assertEquals("PUBLISHED", log.get("to_status"));
        assertEquals("REJECTED", log.get("reason"), "释放后恢复日志 reason 应为本次 release 原因");
        assertEquals(0L, ((Number) log.get("operator_id")).longValue());
        assertEquals("SYSTEM", log.get("operator_role"));
    }

    // ==================== 并发执行骨架 ====================

    /** 并发执行 n 个任务（CountDownLatch 同时放行，Future.get 收结果），返回各任务 Outcome */
    private List<Outcome> runConcurrent(JobTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch ready = new CountDownLatch(N);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return task.run(idx);
            }));
        }
        // 等所有线程就绪后同时放行
        assertTrue(ready.await(10, TimeUnit.SECONDS), "并发线程未在 10s 内全部就绪");
        start.countDown();
        List<Outcome> results = new ArrayList<>();
        try {
            for (Future<Outcome> f : futures) {
                results.add(f.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    /** 并发任务函数：入参为线程序号，返回执行结果 */
    private interface JobTask {
        Outcome run(int idx);
    }

    /** 单个并发任务结果：success=true 表示服务调用成功返回；否则 code=BusinessException.code 或 errorClass=异常类名 */
    private static final class Outcome {
        boolean success;
        Integer code;
        String errorClass;
    }

    // ==================== 任务包装（捕获业务异常并记录 code，不吞未知异常证据） ====================

    private Outcome doReserve(Long jobId, HcReserveRequest req) {
        Outcome o = new Outcome();
        try {
            internalJobService.reserveHc(jobId, req);
            o.success = true;
        } catch (BusinessException e) {
            o.code = e.getCode();
        } catch (Throwable t) {
            o.errorClass = t.getClass().getSimpleName();
        }
        return o;
    }

    private Outcome doConfirm(Long jobId, HcConfirmRequest req) {
        Outcome o = new Outcome();
        try {
            internalJobService.confirmHc(jobId, req);
            o.success = true;
        } catch (BusinessException e) {
            o.code = e.getCode();
        } catch (Throwable t) {
            o.errorClass = t.getClass().getSimpleName();
        }
        return o;
    }

    private Outcome doRelease(Long jobId, HcReleaseRequest req) {
        Outcome o = new Outcome();
        try {
            internalJobService.releaseHc(jobId, req);
            o.success = true;
        } catch (BusinessException e) {
            o.code = e.getCode();
        } catch (Throwable t) {
            o.errorClass = t.getClass().getSimpleName();
        }
        return o;
    }

    // ==================== 数据准备 / 核对 / 清理 ====================

    /** 串行前置 reserve（并发用例准备状态用，确保数据已提交） */
    private void reserveForSetup(Long jobId, long offerId, long candidateId) {
        HcReserveRequest req = new HcReserveRequest();
        req.setCompanyId(currentCompanyId);
        req.setOfferId(offerId);
        req.setCandidateId(candidateId);
        internalJobService.reserveHc(jobId, req);
    }

    /** 串行前置 confirm */
    private void confirmForSetup(Long jobId, long offerId) {
        HcConfirmRequest req = new HcConfirmRequest();
        req.setCompanyId(currentCompanyId);
        req.setOfferId(offerId);
        internalJobService.confirmHc(jobId, req);
    }

    /** 插入已发布测试岗位，返回自增 id */
    private Long insertJob(long companyId, int totalHc, LocalDateTime publishedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, city_code, city_name, total_hc, reserved_hc, confirmed_hc, "
                            + "status, pause_reason, close_reason, closed_at, published_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 0, 0, 'PUBLISHED', NULL, NULL, NULL, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "并发测试岗位");
            ps.setString(3, "110000");
            ps.setString(4, "北京");
            ps.setInt(5, totalHc);
            ps.setTimestamp(6, publishedAt == null ? null : Timestamp.valueOf(publishedAt));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /** 读取岗位计数/状态/字段 */
    private Map<String, Object> queryJob(Long jobId) {
        return jdbcTemplate.queryForMap(
                "SELECT total_hc, reserved_hc, confirmed_hc, status, pause_reason, close_reason, closed_at, published_at "
                        + "FROM job_post WHERE id = ?", jobId);
    }

    /** 当前企业流水条数 */
    private int countReservations(long companyId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_hc_reservation WHERE company_id = ?", Integer.class, companyId);
        return c == null ? 0 : c;
    }

    /** 当前企业状态日志条数 */
    private int countLogs(long companyId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_status_log WHERE company_id = ?", Integer.class, companyId);
        return c == null ? 0 : c;
    }

    /** 当前企业最新一条状态日志 */
    private Map<String, Object> queryLatestStatusLog() {
        return jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, reason, operator_id, operator_role FROM job_status_log "
                        + "WHERE company_id = ? ORDER BY id DESC LIMIT 1", currentCompanyId);
    }

    /** E：并发不变量——reserved>=0、confirmed>=0、sum<=total、结果数==提交数（无超时/线程泄漏） */
    private void assertConcurrencyInvariants(List<Outcome> outcomes, Long jobId) {
        assertEquals(N, outcomes.size(), "结果数应等于提交任务数，不得超时/丢任务");
        Map<String, Object> job = queryJob(jobId);
        int reserved = ((Number) job.get("reserved_hc")).intValue();
        int confirmed = ((Number) job.get("confirmed_hc")).intValue();
        int total = ((Number) job.get("total_hc")).intValue();
        assertTrue(reserved >= 0, "reserved_hc 不得为负，实际=" + reserved);
        assertTrue(confirmed >= 0, "confirmed_hc 不得为负，实际=" + confirmed);
        assertTrue(reserved + confirmed <= total, "reserved+confirmed 不得超 total，实际=" + (reserved + confirmed) + "/" + total);
    }
}

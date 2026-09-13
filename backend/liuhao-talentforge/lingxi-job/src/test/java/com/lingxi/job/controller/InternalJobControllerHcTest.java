package com.lingxi.job.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HC 预冻结核心链路测试
 * <p>真实连接本地 MySQL，前置插入测试岗位，AfterEach 清理，验证 reserve/confirm/release 计数与幂等。</p>
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
class InternalJobControllerHcTest {

    /** 测试专用企业ID，避免与真实数据冲突 */
    private static final long TEST_COMPANY_ID = 999999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 每次测试后清理本测试插入的岗位、流水和状态日志
     * <p>顺序：先 job_status_log、再 job_hc_reservation、最后 job_post（逻辑先子后父；
     * 三表均无外键约束，顺序仅为习惯，job_status_log 无级联必须显式删）。</p>
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_status_log WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_hc_reservation WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
    }

    /**
     * reserve 成功：插入已发布岗位(totalHc=2) → reserve → 响应 reservedHc 应为 1
     * <p>此用例能抓住 P0 缺陷：修复前 confirmedHc 绑定为 NULL，SQL 报错导致 reserve 失败。</p>
     */
    @Test
    void reserve_success() throws Exception {
        Long jobId = insertJob(2);

        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/reserve")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70001L,
                                "candidateId", 80001L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reservationStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.reservedHc").value(1))
                .andExpect(jsonPath("$.data.availableHc").value(1));

        // 数据库校验：reserved_hc 确实 +1
        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_hc FROM job_post WHERE id = ?", Integer.class, jobId);
        assertEquals(1, reserved);
    }

    /**
     * reserve 幂等：同一 offerId 两次 → 两次都成功，且 reserved_hc 只加一次
     */
    @Test
    void reserve_idempotent() throws Exception {
        Long jobId = insertJob(2);
        String body = json(mapOf(
                "companyId", TEST_COMPANY_ID,
                "offerId", 70002L,
                "candidateId", 80002L));

        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/reserve")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 第二次（幂等返回，不重复扣）
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/reserve")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reservationStatus").value("RESERVED"));

        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_hc FROM job_post WHERE id = ?", Integer.class, jobId);
        assertEquals(1, reserved);
    }

    /**
     * companyId 与岗位所属不一致 → code 2203
     */
    @Test
    void reserve_companyMismatch() throws Exception {
        Long jobId = insertJob(2);

        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/reserve")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", 888888L, // 与岗位 999999 不一致
                                "offerId", 70003L,
                                "candidateId", 80003L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2203));
    }

    /**
     * confirm：reserve 后 confirm → reservedHc-1, confirmedHc+1
     */
    @Test
    void confirm_transition() throws Exception {
        Long jobId = insertJob(2);
        reserve(jobId, 70004L, 80004L);

        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/confirm")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70004L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.reservedHc").value(0))
                .andExpect(jsonPath("$.data.confirmedHc").value(1));

        // 数据库校验：job_post 计数转换 reserved 0 / confirmed 1，流水状态 CONFIRMED
        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT reserved_hc, confirmed_hc FROM job_post WHERE id = ?", jobId);
        assertEquals(0, ((Number) postRow.get("reserved_hc")).intValue());
        assertEquals(1, ((Number) postRow.get("confirmed_hc")).intValue());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM job_hc_reservation WHERE offer_id = ?", String.class, 70004L);
        assertEquals("CONFIRMED", status);
    }

    /**
     * release：reserve 后 release → 计数回滚，流水状态 RELEASED
     */
    @Test
    void release_rollback() throws Exception {
        Long jobId = insertJob(2);
        // 完整链路：reserve → confirm → release（从 CONFIRMED 释放，验证 confirmedAt 保留）
        reserve(jobId, 70005L, 80005L);
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/confirm")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70005L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/release")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70005L,
                                "reason", "REJECTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reservationStatus").value("RELEASED"))
                .andExpect(jsonPath("$.data.reservedHc").value(0))
                .andExpect(jsonPath("$.data.confirmedHc").value(0));

        // 数据库校验：confirmed_hc 回滚为 0（原来是 confirmed 1，释放后 -1）
        Integer confirmed = jdbcTemplate.queryForObject(
                "SELECT confirmed_hc FROM job_post WHERE id = ?", Integer.class, jobId);
        assertEquals(0, confirmed);

        // 验证 P1-4 修复：release 后 confirmed_at 仍保留（不被 NULL 覆盖）
        Integer confirmedAtCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_hc_reservation WHERE offer_id = ? AND confirmed_at IS NOT NULL",
                Integer.class, 70005L);
        assertEquals(1, confirmedAtCount);
    }

    /**
     * release 原因非法 → code 400
     */
    @Test
    void release_invalidReason() throws Exception {
        Long jobId = insertJob(2);
        reserve(jobId, 70006L, 80006L);

        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/release")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70006L,
                                "reason", "RANDOM_XXX"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * flow：reserve 后查询 → 返回 RESERVED + reservedAt 非空
     */
    @Test
    void flow_reserved() throws Exception {
        Long jobId = insertJob(2);
        reserve(jobId, 70010L, 80010L);

        mockMvc.perform(get("/internal/jobs/" + jobId + "/hc/flow")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .param("offerId", String.valueOf(70010L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.offerId").value(70010L))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.reservedAt").isNotEmpty());
    }

    /**
     * flow：reserve → confirm 后查询 → CONFIRMED + confirmedAt 非空
     */
    @Test
    void flow_confirmed() throws Exception {
        Long jobId = insertJob(2);
        reserve(jobId, 70011L, 80011L);
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/confirm")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70011L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/internal/jobs/" + jobId + "/hc/flow")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .param("offerId", String.valueOf(70011L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty());
    }

    /**
     * flow：reserve → release 后查询 → RELEASED + releasedAt 非空 + releaseReason
     */
    @Test
    void flow_released() throws Exception {
        Long jobId = insertJob(2);
        reserve(jobId, 70012L, 80012L);
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/release")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", 70012L,
                                "reason", "REJECTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/internal/jobs/" + jobId + "/hc/flow")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .param("offerId", String.valueOf(70012L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("RELEASED"))
                .andExpect(jsonPath("$.data.releasedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.releaseReason").value("REJECTED"));
    }

    /**
     * flow：流水不存在 → code 2202
     */
    @Test
    void flow_notFound() throws Exception {
        Long jobId = insertJob(2);

        mockMvc.perform(get("/internal/jobs/" + jobId + "/hc/flow")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .param("offerId", String.valueOf(99999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2202));
    }

    /**
     * flow：岗位不存在 → code 2101
     */
    @Test
    void flow_jobNotFound() throws Exception {
        mockMvc.perform(get("/internal/jobs/99999999/hc/flow")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .param("offerId", String.valueOf(70013L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /**
     * flow：流水挂在同企业另一岗位（路径 jobId ≠ 流水 jobId）→ 防御校验，code 2202
     */
    @Test
    void flow_jobMismatch_reject2202() throws Exception {
        Long jobIdA = insertJob(2);
        Long jobIdB = insertJob(2);
        reserve(jobIdA, 70014L, 80014L);

        // 用 B 岗位的 jobId 查挂到 A 岗位的 offer 流水 → 2202
        mockMvc.perform(get("/internal/jobs/" + jobIdB + "/hc/flow")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .param("offerId", String.valueOf(70014L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2202));
    }

    // ==================== HC 满额状态自动管理用例（阶段7 状态自动管理） ====================

    /**
     * ①满额暂停：totalHc=1 reserve → 岗位 PAUSED(HC_RESERVED_FULL)、close 字段写死为 NULL；落 SYSTEM 日志（from=PUBLISHED/to=PAUSED）
     */
    @Test
    void reserve_full_pausesToPaused() throws Exception {
        Long jobId = insertJob(1);

        reserve(jobId, 72001L, 82001L);

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, pause_reason, close_reason, closed_at FROM job_post WHERE id = ?", jobId);
        assertEquals("PAUSED", postRow.get("status"));
        assertEquals("HC_RESERVED_FULL", postRow.get("pause_reason"));
        assertNull(postRow.get("close_reason"), "PAUSED 三态字段写死：close_reason 应为 NULL");
        assertNull(postRow.get("closed_at"), "PAUSED 三态字段写死：closed_at 应为 NULL");

        Map<String, Object> logRow = queryLatestStatusLog(jobId);
        assertEquals("PUBLISHED", logRow.get("from_status"));
        assertEquals("PAUSED", logRow.get("to_status"));
        assertEquals("HC_RESERVED_FULL", logRow.get("reason"));
        assertEquals(0L, ((Number) logRow.get("operator_id")).longValue(), "operator_id 应为 0=SYSTEM");
        assertEquals("SYSTEM", logRow.get("operator_role"));
    }

    /**
     * ②释放恢复：满额暂停后 release → 岗位恢复 PUBLISHED、清 pause_reason、published_at 保持原值；日志 reason=本次 release 原因
     */
    @Test
    void release_resumeFromPaused_publishes_publishedAtKept() throws Exception {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 1, 10, 0, 0);
        Long jobId = insertJob(1, "PUBLISHED", null, null, null, publishedAt);
        reserve(jobId, 72002L, 82002L);   // 满 → PAUSED

        release(jobId, 72002L, "REJECTED");

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, pause_reason, close_reason, closed_at, published_at FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", postRow.get("status"));
        assertNull(postRow.get("pause_reason"), "恢复应清空 pause_reason");
        assertNull(postRow.get("close_reason"), "恢复后 close_reason 应为 NULL（三态全套归零）");
        assertNull(postRow.get("closed_at"), "恢复后 closed_at 应为 NULL（三态全套归零）");
        assertEquals(publishedAt, (LocalDateTime) postRow.get("published_at"),
                "PAUSED 恢复不刷新 published_at，应保持原值");

        Map<String, Object> logRow = queryLatestStatusLog(jobId);
        assertEquals("PAUSED", logRow.get("from_status"));
        assertEquals("PUBLISHED", logRow.get("to_status"));
        assertEquals("REJECTED", logRow.get("reason"), "释放后恢复日志 reason 应为本次 release 原因");
        assertEquals("SYSTEM", logRow.get("operator_role"));
    }

    /**
     * ③满额关闭：totalHc=1 reserve 满后 confirm → 岗位 CLOSED(HC_CONFIRMED_FULL)+closed_at、清 pause_reason；日志 from=PAUSED/to=CLOSED
     */
    @Test
    void confirm_full_closes() throws Exception {
        Long jobId = insertJob(1);
        reserve(jobId, 72003L, 82003L);   // 满 → PAUSED

        confirm(jobId, 72003L);

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, pause_reason, close_reason, closed_at FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", postRow.get("status"));
        assertNull(postRow.get("pause_reason"), "CLOSED 三态字段写死：pause_reason 应为 NULL");
        assertEquals("HC_CONFIRMED_FULL", postRow.get("close_reason"));
        assertNotNull(postRow.get("closed_at"), "满额关闭应写 closed_at");

        Map<String, Object> logRow = queryLatestStatusLog(jobId);
        assertEquals("PAUSED", logRow.get("from_status"));
        assertEquals("CLOSED", logRow.get("to_status"));
        assertEquals("HC_CONFIRMED_FULL", logRow.get("reason"));
    }

    /**
     * ④释放重开：满额关闭后 release → 岗位恢复 PUBLISHED、清关闭原因/时间、published_at 晚于关闭前值；日志 reason=本次 release 原因
     */
    @Test
    void release_confirmed_reopens_publishedAtRefreshed() throws Exception {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 1, 10, 0, 0);
        Long jobId = insertJob(1, "PUBLISHED", null, null, null, publishedAt);
        reserve(jobId, 72004L, 82004L);
        confirm(jobId, 72004L);           // 满 → CLOSED

        release(jobId, 72004L, "EXPIRED");

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, pause_reason, close_reason, closed_at, published_at FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", postRow.get("status"));
        assertNull(postRow.get("pause_reason"), "重开后 pause_reason 应为 NULL（三态全套归零）");
        assertNull(postRow.get("close_reason"), "重开应清空 close_reason");
        assertNull(postRow.get("closed_at"), "重开应清空 closed_at");
        LocalDateTime dbPublishedAt = (LocalDateTime) postRow.get("published_at");
        assertTrue(dbPublishedAt.isAfter(publishedAt), "满额关闭重开应刷新 published_at（晚于关闭前值）");

        Map<String, Object> logRow = queryLatestStatusLog(jobId);
        assertEquals("CLOSED", logRow.get("from_status"));
        assertEquals("PUBLISHED", logRow.get("to_status"));
        assertEquals("EXPIRED", logRow.get("reason"));
    }

    /**
     * ⑤手动关闭不恢复：满额关闭后把 close_reason 改为 MANUAL，release → 仍 CLOSED、不落恢复日志
     */
    @Test
    void release_manualClosed_notResumed() throws Exception {
        Long jobId = insertJob(1);
        reserve(jobId, 72005L, 82005L);
        confirm(jobId, 72005L);           // 满 → CLOSED(HC_CONFIRMED_FULL)
        int logsBefore = countStatusLogs(jobId);
        jdbcTemplate.update("UPDATE job_post SET close_reason = 'MANUAL' WHERE id = ?", jobId);

        release(jobId, 72005L, "REJECTED");

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", postRow.get("status"));
        assertEquals("MANUAL", postRow.get("close_reason"), "MANUAL 关闭原因不应被覆盖");
        assertEquals(logsBefore, countStatusLogs(jobId), "MANUAL 关闭岗位 release 不应新增恢复日志");
    }

    /**
     * ⑤补充：CLOSED+EXPIRED 释放不恢复——满额关闭后把 close_reason 改为 EXPIRED，release → 仍 CLOSED、不落恢复日志
     */
    @Test
    void release_expiredClosed_notResumed() throws Exception {
        Long jobId = insertJob(1);
        reserve(jobId, 72006L, 82006L);
        confirm(jobId, 72006L);           // 满 → CLOSED(HC_CONFIRMED_FULL)
        int logsBefore = countStatusLogs(jobId);
        jdbcTemplate.update("UPDATE job_post SET close_reason = 'EXPIRED' WHERE id = ?", jobId);

        release(jobId, 72006L, "REJECTED");

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, close_reason FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", postRow.get("status"));
        assertEquals("EXPIRED", postRow.get("close_reason"));
        assertEquals(logsBefore, countStatusLogs(jobId), "EXPIRED 关闭岗位 release 不应新增恢复日志");
    }

    /**
     * ⑥已关闭不覆盖：岗位 CLOSED(HC_CONFIRMED_FULL) 后再 confirm 未完成名额 → 只改计数、不覆盖 close_reason、不落新日志
     */
    @Test
    void confirm_alreadyClosed_notOverwrite() throws Exception {
        Long jobId = insertJob(2);
        reserve(jobId, 72007L, 82007L);
        confirm(jobId, 72007L);           // confirmed=1（未满）
        reserve(jobId, 72008L, 82008L);   // reserved=1, confirmed=1
        jdbcTemplate.update("UPDATE job_post SET status = 'CLOSED', close_reason = 'HC_CONFIRMED_FULL', closed_at = NOW() WHERE id = ?", jobId);
        int logsBefore = countStatusLogs(jobId);

        confirm(jobId, 72008L);           // confirmed=2==total，但岗位已 CLOSED

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT confirmed_hc, status, close_reason, closed_at FROM job_post WHERE id = ?", jobId);
        assertEquals(2, ((Number) postRow.get("confirmed_hc")).intValue(), "计数应正常转换");
        assertEquals("CLOSED", postRow.get("status"));
        assertEquals("HC_CONFIRMED_FULL", postRow.get("close_reason"), "已关闭岗位 close_reason 不应覆盖");
        assertNotNull(postRow.get("closed_at"), "closed_at 保持原值");
        assertEquals(logsBefore, countStatusLogs(jobId), "已关闭岗位 confirm 不应新增状态日志");
    }

    /**
     * ⑦已到期不恢复（CLOSED）：满额关闭且已到期后 release → 仍 CLOSED、只回退计数、不落恢复日志
     */
    @Test
    void release_expiredConfirmed_notResumed() throws Exception {
        Long jobId = insertJob(1, "PUBLISHED", null, null, LocalDateTime.now().minusDays(1), null);
        reserve(jobId, 72009L, 82009L);
        confirm(jobId, 72009L);           // 满 → CLOSED（岗位已过期）
        int logsBefore = countStatusLogs(jobId);

        release(jobId, 72009L, "REJECTED");

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, close_reason, confirmed_hc FROM job_post WHERE id = ?", jobId);
        assertEquals("CLOSED", postRow.get("status"), "已到期岗位释放不应恢复 PUBLISHED");
        assertEquals("HC_CONFIRMED_FULL", postRow.get("close_reason"));
        assertEquals(0, ((Number) postRow.get("confirmed_hc")).intValue(), "计数应正常回退");
        assertEquals(logsBefore, countStatusLogs(jobId), "已到期岗位释放不应新增恢复日志");
    }

    /**
     * ⑦已到期不恢复（PAUSED）：预占满暂停且已到期后 release → 仍 PAUSED、只回退计数、不落恢复日志
     */
    @Test
    void release_expiredPaused_notResumed() throws Exception {
        Long jobId = insertJob(1, "PUBLISHED", null, null, LocalDateTime.now().minusDays(1), null);
        reserve(jobId, 72010L, 82010L);   // 满 → PAUSED（岗位已过期）
        int logsBefore = countStatusLogs(jobId);

        release(jobId, 72010L, "REJECTED");

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT status, pause_reason, reserved_hc FROM job_post WHERE id = ?", jobId);
        assertEquals("PAUSED", postRow.get("status"), "已到期岗位释放不应恢复 PUBLISHED");
        assertEquals("HC_RESERVED_FULL", postRow.get("pause_reason"));
        assertEquals(0, ((Number) postRow.get("reserved_hc")).intValue(), "计数应正常回退");
        assertEquals(logsBefore, countStatusLogs(jobId), "已到期岗位释放不应新增恢复日志");
    }

    // ==================== 辅助方法 ====================

    /**
     * 插入一个已发布的测试岗位（totalHc 可配；状态/原因/到期/发布时间用默认值）
     */
    private Long insertJob(int totalHc) {
        return insertJob(totalHc, "PUBLISHED", null, null, null, null);
    }

    /**
     * 插入测试岗位（参数化 status/pause_reason/close_reason/expires_at/published_at）
     */
    private Long insertJob(int totalHc, String status, String pauseReason, String closeReason,
                           LocalDateTime expiresAt, LocalDateTime publishedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, city_code, city_name, total_hc, reserved_hc, confirmed_hc, "
                            + "status, pause_reason, close_reason, expires_at, published_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, TEST_COMPANY_ID);
            ps.setString(2, "HC测试岗位");
            ps.setString(3, "110000");
            ps.setString(4, "北京");
            ps.setInt(5, totalHc);
            ps.setString(6, status);
            ps.setString(7, pauseReason);
            ps.setString(8, closeReason);
            ps.setTimestamp(9, expiresAt == null ? null : Timestamp.valueOf(expiresAt));
            ps.setTimestamp(10, publishedAt == null ? null : Timestamp.valueOf(publishedAt));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 执行一次 confirm（供满额关闭用例准备前置状态）
     */
    private void confirm(Long jobId, long offerId) throws Exception {
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/confirm")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf("companyId", TEST_COMPANY_ID, "offerId", offerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 执行一次 release（供恢复/不恢复用例触发）
     */
    private void release(Long jobId, long offerId, String reason) throws Exception {
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/release")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf("companyId", TEST_COMPANY_ID, "offerId", offerId, "reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 查询该岗位最新一条状态日志（按 id 倒序取最新）
     */
    private Map<String, Object> queryLatestStatusLog(Long jobId) {
        return jdbcTemplate.queryForMap(
                "SELECT from_status, to_status, reason, operator_id, operator_role FROM job_status_log "
                        + "WHERE job_id = ? ORDER BY id DESC LIMIT 1", jobId);
    }

    /**
     * 查询该岗位状态日志条数
     */
    private int countStatusLogs(Long jobId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_status_log WHERE job_id = ?", Integer.class, jobId);
        return count == null ? 0 : count;
    }

    /**
     * 执行一次 reserve（供 confirm/release 测试准备前置状态）
     */
    private void reserve(Long jobId, long offerId, long candidateId) throws Exception {
        mockMvc.perform(post("/internal/jobs/" + jobId + "/hc/reserve")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(mapOf(
                                "companyId", TEST_COMPANY_ID,
                                "offerId", offerId,
                                "candidateId", candidateId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 简易 Map 构造（JDK 8 无 Map.of，用辅助方法代替）
     */
    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /**
     * Map → JSON 字符串
     */
    private String json(Map<String, Object> map) throws Exception {
        return objectMapper.writeValueAsString(map);
    }
}

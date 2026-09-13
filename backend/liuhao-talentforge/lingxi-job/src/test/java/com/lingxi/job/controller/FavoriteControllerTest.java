package com.lingxi.job.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端岗位收藏接口测试（阶段6.2）
 * <p>真实连接本地 MySQL，前置插入已发布岗位。cleanup 按 insertJob() 记录的精确 jobId
 * 清理收藏/画像/岗位，不按 candidate_id/company_id 宽泛删除（候选人与企业均为真实业务维度，
 * 宽泛删除可能误伤非测试数据）。候选人 A/B 用独占高位 ID（99990001/99990002）进一步避碰真实用户。
 * 覆盖幂等（含并发）/角色（HR+INTERVIEWER）/隔离/分页边界/
 * 已关闭/已暂停/已删除/物理缺失岗位/ids/industryName。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class FavoriteControllerTest {

    /** 测试专用企业ID，避免与 HR/Job 测试（666880/888880/777777）冲突 */
    private static final long TEST_COMPANY_ID = 777778L;

    /** 候选人 A（测试专用独占高位ID：真实用户 1~3、文档示例 1001 之外，cleanup 只删本测试数据） */
    private static final long CANDIDATE_A = 99990001L;

    /** 候选人 B（隔离验证方，独占高位ID） */
    private static final long CANDIDATE_B = 99990002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试实际创建的岗位ID，cleanup 按精确 ID 清理，避免按 company/candidate 宽泛删除误伤非测试数据 */
    private final List<Long> createdJobIds = new ArrayList<>();

    /**
     * 每次测试后按 insertJob() 记录的精确 jobId 清理（先收藏→画像→岗位）。
     * <p>不按 candidate_id / company_id 宽泛删除：候选人为真实/示例用户时可能误删其存量收藏，
     * company_id 也可能被其他环境复用——精确清理不依赖任何 ID 独占假设。</p>
     */
    @AfterEach
    void cleanup() {
        for (Long jobId : createdJobIds) {
            jdbcTemplate.update("DELETE FROM job_favorite WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_profile WHERE job_id = ?", jobId);
            jdbcTemplate.update("DELETE FROM job_post WHERE id = ?", jobId);
        }
        createdJobIds.clear();
    }

    // ==================== 收藏（幂等） ====================

    /**
     * 用例1：候选人首次收藏成功 → code=200 + DB 1 条
     */
    @Test
    void favorite_firstTime() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);

        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.favorited").value(true));
        assertEquals(1, favoriteCount(CANDIDATE_A, jobId), "收藏后应存在 1 条收藏");
    }

    /**
     * 用例2：重复收藏幂等 → 仍 code=200（不抛 2106），DB 仍 1 条
     */
    @Test
    void favorite_idempotent() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobId);

        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favorited").value(true));
        assertEquals(1, favoriteCount(CANDIDATE_A, jobId), "重复收藏不应新增行");
    }

    /**
     * 用例3：取消收藏成功 → code=200 + DB 0 条
     */
    @Test
    void unfavorite_removes() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobId);

        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.favorited").value(false));
        assertEquals(0, favoriteCount(CANDIDATE_A, jobId), "取消后应无收藏记录");
    }

    /**
     * 用例4：未收藏取消幂等 → code=200
     */
    @Test
    void unfavorite_notFavorited_idempotent() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);

        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favorited").value(false));
    }

    /**
     * 用例5：非 CANDIDATE 拒绝 → code=403（收藏/列表/ids 三端点；HR 与 INTERVIEWER 均非 CANDIDATE）
     */
    @Test
    void favorite_nonCandidate_403() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);

        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(hrHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/v1/favorites").headers(hrHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/v1/favorites/ids").headers(hrHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        // INTERVIEWER 同样非 CANDIDATE → 403
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(interviewerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        assertEquals(0, favoriteCount(CANDIDATE_A, jobId), "HR/INTERVIEWER 收藏不应落库");
    }

    /**
     * 用例6：收藏不存在/已暂停/已关闭/已删除（非 PUBLISHED）岗位 → code=2101（沿用岗位不可见语义），不落库
     */
    @Test
    void favorite_nonPublishable_2101() throws Exception {
        Long pausedId = insertJob(TEST_COMPANY_ID, "PAUSED", false);
        Long closedId = insertJob(TEST_COMPANY_ID, "CLOSED", false);
        Long deletedId = insertJob(TEST_COMPANY_ID, "PUBLISHED", true);

        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", pausedId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", closedId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", deletedId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", 999999999L)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
        assertEquals(0, favoriteCountOf(CANDIDATE_A), "收藏失败不应落库");
    }

    /**
     * 用例13：favorited 漏传/传 null → HTTP 400 + code 400（Boolean+@NotNull，非误判取消）
     */
    @Test
    void favorite_missingFavorited_400() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);

        // 空 body → favorited=null → @NotNull → MethodArgumentNotValidException → HTTP 400
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 显式 null → 同样 400
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(CANDIDATE_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 未被误判为取消：无收藏记录且未插入
        assertEquals(0, favoriteCount(CANDIDATE_A, jobId), "漏传 favorited 不应产生任何收藏变更");
    }

    /**
     * 并发幂等：两个线程同时收藏同一岗位 → 均 code=200，DB 最终仅 1 条
     * <p>说明（如实）：唯一键兜底分支（DuplicateKeyException）是否被实际命中取决于线程调度——
     * 若第二个请求先看到第一个已 commit 的行，走 selectByCandidateAndJob 短路分支而非 catch。
     * 本用例验证的是「并发下幂等成功」的最终态，而非确定性覆盖 catch 分支（后者无法在单测中确定性触发）。</p>
     */
    @Test
    void favorite_concurrentIdempotent() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                                    .headers(candidateHeaders(CANDIDATE_A))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"favorited\":true}"))
                            .andReturn();
                    return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                            .path("code").asInt();
                }));
            }
            // 两线程同时起跑，触发真实并发
            ready.await();
            start.countDown();
            for (Future<Integer> future : futures) {
                assertEquals(200, future.get(15, TimeUnit.SECONDS), "并发收藏应均成功");
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, favoriteCount(CANDIDATE_A, jobId), "并发收藏后仅一条收藏记录");
    }

    // ==================== 收藏列表 ====================

    /**
     * 用例7 + 用例12：候选人隔离 —— A 列表/ids 不含 B 收藏的岗位，B 同理
     */
    @Test
    void favorites_isolationBetweenCandidates() throws Exception {
        Long jobA = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        Long jobB = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobA);
        favoriteAs(CANDIDATE_B, jobB);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobA));
        mockMvc.perform(get("/api/v1/favorites/ids").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(jobA));
        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobB));
    }

    /**
     * 用例8：分页与稳定排序 —— favoritedAt DESC, id DESC（同秒时 id DESC 兜底），page/size 生效
     */
    @Test
    void favorites_pagingAndOrder() throws Exception {
        Long jobA = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        Long jobB = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        Long jobC = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobA);
        favoriteAs(CANDIDATE_A, jobB);
        favoriteAs(CANDIDATE_A, jobC);

        // 全部返回：新收藏在前（favoritedAt DESC, id DESC 兜底）
        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobC))
                .andExpect(jsonPath("$.data.list[1].jobId").value(jobB))
                .andExpect(jsonPath("$.data.list[2].jobId").value(jobA));
        // 分页生效：page=1 size=2 → 仅 2 条，最新 2 条在前
        mockMvc.perform(get("/api/v1/favorites?page=1&size=2").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobC))
                .andExpect(jsonPath("$.data.list[1].jobId").value(jobB));
    }

    /**
     * 用例9：已关闭岗位仍在列表 → isOffline=true, deleted=false，且仍可取消收藏
     */
    @Test
    void favorites_closedJobStillListed_offline() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobId);
        jdbcTemplate.update("UPDATE job_post SET status='CLOSED', closed_at=NOW() WHERE id=?", jobId);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data.list[0].isOffline").value(true))
                .andExpect(jsonPath("$.data.list[0].deleted").value(false));
        // 取消不校验岗位状态 → 仍可取消
        unfavoriteAs(CANDIDATE_A, jobId);
        assertEquals(0, favoriteCount(CANDIDATE_A, jobId), "已关闭岗位仍可取消收藏");
    }

    /**
     * 已暂停岗位仍在列表 → isOffline=true, deleted=false（PAUSED 是 job_post 真实状态）
     * <p>注：OFFLINE 非存储层独立状态（违规下架实际落 CLOSED+close_reason=VIOLATION），已被 CLOSED 用例覆盖。</p>
     */
    @Test
    void favorites_pausedJob_offline() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobId);
        jdbcTemplate.update("UPDATE job_post SET status='PAUSED' WHERE id=?", jobId);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data.list[0].isOffline").value(true))
                .andExpect(jsonPath("$.data.list[0].deleted").value(false));
    }

    /**
     * 用例10：已逻辑删除岗位 → deleted=true + isOffline=true，记录不丢，且仍可取消
     */
    @Test
    void favorites_softDeletedJob_keptAndFlagged() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobId);
        jdbcTemplate.update("UPDATE job_post SET deleted_at=NOW() WHERE id=?", jobId);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data.list[0].deleted").value(true))
                .andExpect(jsonPath("$.data.list[0].isOffline").value(true));
        unfavoriteAs(CANDIDATE_A, jobId);
        assertEquals(0, favoriteCount(CANDIDATE_A, jobId), "已删除岗位仍可取消收藏");
    }

    /**
     * 用例11：收藏/取消后列表实时一致（收藏 +1，取消 -1）
     */
    @Test
    void favorites_listReflectsFavoriteAndUnfavorite() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(0));
        favoriteAs(CANDIDATE_A, jobId);
        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(1));
        unfavoriteAs(CANDIDATE_A, jobId);
        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /**
     * 用例15：分页边界 —— page<1 / page>100 / size<1 / size>50 → HTTP 200 + code 400
     */
    @Test
    void favorites_pageBoundary_400() throws Exception {
        mockMvc.perform(get("/api/v1/favorites?page=0").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/favorites?page=101").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/favorites?size=0").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/favorites?size=51").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 用例16：岗位物理缺失（LEFT JOIN）→ 收藏记录仍返回，jobId 仍存在（取 f.job_id），
     * 卡片其他字段 null + isOffline=true + deleted=true，可据此 jobId 取消收藏
     */
    @Test
    void favorites_physicallyMissingJob_kept() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        favoriteAs(CANDIDATE_A, jobId);
        // 物理删除岗位行（非软删），模拟历史收藏指向已物理清除岗位
        jdbcTemplate.update("DELETE FROM job_post WHERE id=?", jobId);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data.list[0].title").isEmpty())
                .andExpect(jsonPath("$.data.list[0].isOffline").value(true))
                .andExpect(jsonPath("$.data.list[0].deleted").value(true));
        // 可据此 jobId 取消收藏 → 成功，DB 清除
        unfavoriteAs(CANDIDATE_A, jobId);
        assertEquals(0, favoriteCount(CANDIDATE_A, jobId), "物理缺失岗位仍可取消收藏");
    }

    /**
     * 用例17：收藏列表 industryName = IndustryCodeEnum.fromCode(industryGroupCode).getDesc()（非 null）
     */
    @Test
    void favorites_industryName() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);  // IT
        favoriteAs(CANDIDATE_A, jobId);

        mockMvc.perform(get("/api/v1/favorites").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(jsonPath("$.data.list[0].industryGroupCode").value("IT"))
                .andExpect(jsonPath("$.data.list[0].industryName").value("互联网/IT"))
                .andExpect(jsonPath("$.data.list[0].favoritedAt").exists())
                .andExpect(jsonPath("$.data.list[0].companyName").isEmpty());
    }

    // ==================== favorites/ids ====================

    /**
     * 用例14：favorites/ids 返回已收藏集合（收藏时间倒序），空候选人返回 []
     */
    @Test
    void favoriteIds_returnsCollectionAndEmpty() throws Exception {
        Long jobA = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        Long jobB = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);

        // 候选人 B 无收藏 → []
        mockMvc.perform(get("/api/v1/favorites/ids").headers(candidateHeaders(CANDIDATE_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        // A 收藏 2 条 → ids=[jobB, jobA]（created_at DESC, id DESC）
        favoriteAs(CANDIDATE_A, jobA);
        favoriteAs(CANDIDATE_A, jobB);
        mockMvc.perform(get("/api/v1/favorites/ids").headers(candidateHeaders(CANDIDATE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value(jobB))
                .andExpect(jsonPath("$.data[1]").value(jobA));
    }

    // ==================== 鉴权 ====================

    /**
     * 未登录 → code=401（类级 @RequireLogin，收藏与列表均拦截）
     */
    @Test
    void favorite_noLogin_401() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", false);
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 辅助方法 ====================

    /** C端候选人请求头（模拟网关注入） */
    private HttpHeaders candidateHeaders(long candidateId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(candidateId));
        headers.set("X-User-Role", "CANDIDATE");
        return headers;
    }

    /** HR 请求头（角色限制验证用） */
    private HttpHeaders hrHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "HR");
        return headers;
    }

    /** INTERVIEWER 请求头（角色限制验证用） */
    private HttpHeaders interviewerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "3");
        headers.set("X-User-Role", "INTERVIEWER");
        return headers;
    }

    /** 收藏（断言 code=200） */
    private void favoriteAs(long candidateId, long jobId) throws Exception {
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 取消收藏（断言 code=200） */
    private void unfavoriteAs(long candidateId, long jobId) throws Exception {
        mockMvc.perform(post("/api/v1/jobs/{id}/favorite", jobId)
                        .headers(candidateHeaders(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 指定候选人+岗位的收藏条数 */
    private int favoriteCount(long candidateId, long jobId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_favorite WHERE candidate_id=? AND job_id=?",
                Integer.class, candidateId, jobId);
        return cnt == null ? 0 : cnt;
    }

    /** 指定候选人的收藏总条数 */
    private int favoriteCountOf(long candidateId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_favorite WHERE candidate_id=?",
                Integer.class, candidateId);
        return cnt == null ? 0 : cnt;
    }

    /**
     * 插入测试岗位（默认 IT 行业/北京/薪资 100000~200000/BACHELOR/2年，供收藏与列表字段断言）
     *
     * @param deleted 是否软删除
     */
    private Long insertJob(long companyId, String status, boolean deleted) {
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
            ps.setString(2, "【FAV】Java后端工程师");
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
            ps.setString(18, status);
            ps.setTimestamp(19, Timestamp.valueOf(LocalDateTime.now()));
            if (deleted) {
                ps.setTimestamp(20, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                ps.setNull(20, Types.TIMESTAMP);
            }
            ps.setLong(21, CANDIDATE_A);
            return ps;
        }, keyHolder);
        Long jobId = keyHolder.getKey().longValue();
        createdJobIds.add(jobId);
        return jobId;
    }
}

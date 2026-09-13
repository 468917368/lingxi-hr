package com.lingxi.job.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.UserFeignClient;
import com.lingxi.job.feign.dto.UserInfoDTO;
import com.lingxi.job.validator.JobProfileInputSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B端 HR 岗位管理接口测试（阶段2）
 * <p>模拟网关注入 X-User-Id/X-User-Role/X-Company-Id 头，真实连接本地 MySQL，
 * 前置插入测试岗位，AfterEach 清理，覆盖鉴权/创建/编辑/删除/状态机/企业隔离/列表。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class HrJobControllerTest {

    /** 测试专用企业ID */
    private static final long TEST_COMPANY_ID = 999999L;

    /** 跨企业隔离测试用企业ID */
    private static final long OTHER_COMPANY_ID = 888888L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** lingxi-user 批量查询 Mock（测试环境无 lingxi-user 在线，确定性 stub） */
    @MockBean
    private UserFeignClient userFeignClient;

    /**
     * 每次测试前确保城市字典表存在并幂等补齐测试城市
     * <p>测试库可能未执行 init_city_dict.sql：建表（IF NOT EXISTS）安全；
     * INSERT IGNORE 只补缺失行，不覆盖库中已有的城市（含真实 8 城）与停用状态。</p>
     */
    @BeforeEach
    void ensureCityDict() {
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS job_city_dict ("
                + "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, "
                + "code VARCHAR(32) NOT NULL, "
                + "name VARCHAR(64) NOT NULL, "
                + "status TINYINT NOT NULL DEFAULT 1, "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (id), UNIQUE KEY uk_job_city_dict_code (code)) "
                + "ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE=utf8mb4_unicode_ci");
        // 旧版结构（code 主键）迁移：加 id 自增主键 + code 唯一键，保留数据
        List<String> cityCols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_city_dict'", String.class);
        if (!cityCols.contains("id")) {
            jdbcTemplate.update("ALTER TABLE job_city_dict "
                    + "ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT FIRST, "
                    + "DROP PRIMARY KEY, ADD PRIMARY KEY (id), "
                    + "ADD UNIQUE KEY uk_job_city_dict_code (code)");
        }
        // 已存在表修正 code 列 collation 为 unicode_ci（库级默认，MySQL 5.7/8/MariaDB 通用），与 job_post 一致避免 JOIN 1267
        jdbcTemplate.update("ALTER TABLE job_city_dict MODIFY code VARCHAR(32) COLLATE utf8mb4_unicode_ci NOT NULL");
        jdbcTemplate.update("INSERT IGNORE INTO job_city_dict (code, name, status) VALUES "
                + "('110100', '北京', 1), "
                + "('910101', '测试城A', 1), "
                + "('910102', '测试停用城', 0), "
                + "('910103', '测试停用城B', 0)");
    }

    /**
     * 每次测试后清理两个测试企业插入的数据与测试城市字典行
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_hc_reservation WHERE company_id IN (?, ?)", TEST_COMPANY_ID, OTHER_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id IN (?, ?)", TEST_COMPANY_ID, OTHER_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id IN (?, ?)", TEST_COMPANY_ID, OTHER_COMPANY_ID);
        // 只清理测试专用城市（91010x），不触碰真实 8 城
        jdbcTemplate.update("DELETE FROM job_city_dict WHERE code LIKE '91010%'");
    }

    // ==================== 鉴权 ====================

    /**
     * 未登录（无 X-User-Id）→ 401，验证类级 @RequireLogin 生效
     */
    @Test
    void noLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/hr/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * 已登录但非 HR 角色 → 403，验证方法级 @RequireRole("HR") 生效
     */
    @Test
    void nonHrRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/hr/jobs").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /**
     * HR 但无 companyId（非企业用户）→ 业务错误 403
     */
    @Test
    void hrWithoutCompanyId_returnsBusinessError() throws Exception {
        mockMvc.perform(get("/api/v1/hr/jobs").headers(hrNoCompanyHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== 创建 ====================

    /**
     * 创建成功：job_post + job_profile 双表落库，status=DRAFT
     */
    @Test
    void create_success() throws Exception {
        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.profileVersion").value(1))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        Long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM job_post WHERE company_id = ? ORDER BY id DESC LIMIT 1", Long.class, TEST_COMPANY_ID);
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId));
        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_profile WHERE job_id = ?", Integer.class, jobId);
        assertEquals(1, profileCount);
    }

    /**
     * 薪资非法：negotiable=false 且 min>max → 400
     */
    @Test
    void create_salaryInvalid() throws Exception {
        Map<String, Object> body = createBody();
        Map<String, Object> salary = (Map<String, Object>) body.get("salary");
        salary.put("minAmount", 300000L);
        salary.put("maxAmount", 100000L);

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 面议薪资也必须提供合法薪资周期：negotiable=true 且缺 period → 400
     */
    @Test
    void create_salaryNegotiableMissingPeriod() throws Exception {
        Map<String, Object> body = createBody();
        Map<String, Object> salary = (Map<String, Object>) body.get("salary");
        salary.put("negotiable", true);
        salary.remove("period");

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 过去的 expiresAt → HTTP 400 + code 400（@Future 参数校验失败走 HTTP 400，与全局异常处理器一致）
     */
    @Test
    void create_expiresAtPast() throws Exception {
        Map<String, Object> body = createBody();
        body.put("expiresAt", "2020-01-01T00:00:00");

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 技能标签清洗（JobProfileInputSanitizer 接线） ====================

    /**
     * 创建：coreSkills 命中广告（组合特征）→ HTTP 200 + code=400 + 统一提示，且无 DB 写入
     */
    @Test
    void create_coreSkillAdRejected() throws Exception {
        Map<String, Object> body = createBody();
        Map<String, Object> profile = (Map<String, Object>) body.get("profile");
        profile.put("coreSkills", Collections.singletonList(skillMap("加微信13800000001")));

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(JobProfileInputSanitizer.AD_MESSAGE));

        // 广告拒绝发生在任何 DB 写操作之前：job_post/job_profile 均无该企业新行
        Integer postCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_post WHERE company_id = ?", Integer.class, TEST_COMPANY_ID);
        assertEquals(0, postCount);
        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_profile WHERE company_id = ?", Integer.class, TEST_COMPANY_ID);
        assertEquals(0, profileCount);
    }

    /**
     * 创建：softSkills 命中广告（强特征邮箱）→ HTTP 200 + code=400（软能力标签同样受控）
     */
    @Test
    void create_softSkillAdRejected() throws Exception {
        Map<String, Object> body = createBody();
        Map<String, Object> profile = (Map<String, Object>) body.get("profile");
        Map<String, Object> soft = new HashMap<>();
        soft.put("name", "联系QQ号123");
        soft.put("importance", "3");
        profile.put("softSkills", Collections.singletonList(soft));

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(JobProfileInputSanitizer.AD_MESSAGE));
    }

    /**
     * 创建：合法特殊字符技能（C++/C#/.NET/Node.js/CI/CD）→ HTTP 200 + code=200 + 落库正确
     */
    @Test
    void create_legalSpecialCharSkills() throws Exception {
        Map<String, Object> body = createBody();
        Map<String, Object> profile = (Map<String, Object>) body.get("profile");
        List<Map<String, Object>> coreSkills = new ArrayList<>();
        for (String name : new String[]{"C++", "C#", ".NET", "Node.js", "CI/CD", "Spring Boot"}) {
            coreSkills.add(skillMap(name));
        }
        profile.put("coreSkills", coreSkills);

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        Long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM job_post WHERE company_id = ? ORDER BY id DESC LIMIT 1", Long.class, TEST_COMPANY_ID);
        String coreSkillsJson = jdbcTemplate.queryForObject(
                "SELECT core_skills FROM job_profile WHERE job_id = ?", String.class, jobId);
        for (String name : new String[]{"C++", "C#", ".NET", "Node.js", "CI/CD", "Spring Boot"}) {
            assertTrue(coreSkillsJson.contains(name), "落库 core_skills 应含 " + name + "，实际: " + coreSkillsJson);
        }
    }

    // ==================== 编辑 ====================

    /**
     * 更新：画像命中广告 → HTTP 200 + code=400；写前清洗生效——job_post/job_profile 双版本均未变化
     */
    @Test
    void update_adRejected_versionUnchanged() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("version", 0);
        body.put("profile", adProfileMap());
        body.put("profileConfirmed", true);
        body.put("profileVersion", 1);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(JobProfileInputSanitizer.AD_MESSAGE));

        // 写前清洗：job_post 未变（title/version），job_profile 未变（version）
        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT title, version FROM job_post WHERE id = ?", jobId);
        assertEquals("Java后端工程师", postRow.get("title"));
        assertEquals(0, ((Number) postRow.get("version")).intValue());
        Integer profileVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM job_profile WHERE job_id = ?", Integer.class, jobId);
        assertEquals(1, profileVersion);
    }

    /**
     * 非 DRAFT 岗位编辑 → 2104
     */
    @Test
    void update_notDraft() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "PUBLISHED", null, true, 2);
        Map<String, Object> body = baseFields();
        body.put("version", 0);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2104));
    }

    /**
     * profile 有值但缺 profileVersion/profileConfirmed（三件套不完整）→ 400
     */
    @Test
    void update_missingProfileVersion() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("version", 0);
        body.put("profile", profileMap());

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 配对校验先于写操作，job_post 未变
        assertEquals("Java后端工程师", jdbcTemplate.queryForObject(
                "SELECT title FROM job_post WHERE id = ?", String.class, jobId));
    }

    /**
     * job_post 版本冲突 → 2102
     */
    @Test
    void update_versionConflict() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("version", 5);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2102));

        assertEquals("Java后端工程师", jdbcTemplate.queryForObject(
                "SELECT title FROM job_post WHERE id = ?", String.class, jobId));
    }

    /**
     * 不传画像三件套 → 只改岗位（job_post version+1），画像 version 不变
     */
    @Test
    void update_withoutProfile() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("title", "新标题");
        body.put("version", 0);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.profileVersion").value(1));

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT title, version FROM job_post WHERE id = ?", jobId);
        assertEquals("新标题", postRow.get("title"));
        assertEquals(1, ((Number) postRow.get("version")).intValue());
        Integer profileVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM job_profile WHERE job_id = ?", Integer.class, jobId);
        assertEquals(1, profileVersion);
    }

    /**
     * 画像版本冲突 → 2102，且 job_post 已更新需整体回滚（双表事务）
     */
    @Test
    void update_profileVersionConflict() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("version", 0);
        body.put("profile", profileMap());
        body.put("profileConfirmed", true);
        body.put("profileVersion", 9);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2102));

        // job_post 先更新成功后被回滚 → version 仍为 0，title 未变
        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT title, version FROM job_post WHERE id = ?", jobId);
        assertEquals("Java后端工程师", postRow.get("title"));
        assertEquals(0, ((Number) postRow.get("version")).intValue());
        // 画像未变
        Integer profileVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM job_profile WHERE job_id = ?", Integer.class, jobId);
        assertEquals(1, profileVersion);
    }

    // ==================== 删除 ====================

    /**
     * 删除 DRAFT 草稿 → 成功且 deleted_at 非空
     */
    @Test
    void delete_draft() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);

        mockMvc.perform(delete("/api/v1/hr/jobs/" + jobId + "?version=0")
                        .headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Timestamp deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM job_post WHERE id = ?", Timestamp.class, jobId);
        assertNotNull(deletedAt);
    }

    /**
     * 删除版本冲突 → 2102，deleted_at 仍为空
     */
    @Test
    void delete_versionConflict() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);

        mockMvc.perform(delete("/api/v1/hr/jobs/" + jobId + "?version=9")
                        .headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2102));

        Timestamp deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM job_post WHERE id = ?", Timestamp.class, jobId);
        assertNull(deletedAt);
    }

    // ==================== 状态机 ====================

    /**
     * 发布成功：DRAFT + 画像已确认 → PUBLISHED，publishedAt 非空
     */
    @Test
    void publish_success() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "PUBLISH", "version", 0, "profileVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.version").value(1));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, published_at FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", row.get("status"));
        assertNotNull(row.get("published_at"));
    }

    /**
     * 画像核心技能为空数组（core_skills="[]"）→ 发布失败 400
     */
    @Test
    void publish_emptyCoreSkills() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        jdbcTemplate.update("UPDATE job_profile SET core_skills = ? WHERE job_id = ?", "[]", jobId);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "PUBLISH", "version", 0, "profileVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 发布失败，岗位仍为 DRAFT
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM job_post WHERE id = ?", String.class, jobId);
        assertEquals("DRAFT", status);
    }

    /**
     * PUBLISH 缺 profileVersion → 400
     */
    @Test
    void publish_missingProfileVersion() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "PUBLISH", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 画像未确认（confirmedAt 为空）→ 发布失败 400
     */
    @Test
    void publish_profileNotConfirmed() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "DRAFT", null, false, 2);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "PUBLISH", "version", 0, "profileVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * DRAFT 直接 CLOSE（非法迁移）→ 2103
     */
    @Test
    void close_illegalTransition() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "CLOSE", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2103));
    }

    /**
     * CLOSED(MANUAL) 不可 REOPEN → 2103
     */
    @Test
    void reopen_illegal() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "CLOSED", "MANUAL", true, 2);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "REOPEN", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2103));
    }

    /**
     * CLOSED(HC_CONFIRMED_FULL) 且可用HC>0 可 REOPEN → closeReason/closedAt 被清空
     */
    @Test
    void reopen_success() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID, "CLOSED", "HC_CONFIRMED_FULL", true, 2);

        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "REOPEN", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 精确 SQL 清空验证：close_reason/closed_at 必须为 NULL
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, close_reason, closed_at, published_at FROM job_post WHERE id = ?", jobId);
        assertEquals("PUBLISHED", row.get("status"));
        assertNull(row.get("close_reason"));
        assertNull(row.get("closed_at"));
        assertNotNull(row.get("published_at"));
    }

    // ==================== 企业隔离 ====================

    /**
     * 企业A 查企业B 的岗位详情 → 2101（不泄露其他企业岗位）
     */
    @Test
    void crossCompany_get() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);

        mockMvc.perform(get("/api/v1/hr/jobs/" + jobId).headers(hrHeaders(OTHER_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /**
     * 跨企业 编辑/删除/状态变更 → 2101 且数据库内容不变
     */
    @Test
    void crossCompany_write() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("version", 0);

        // PUT 跨企业
        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(OTHER_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
        // PATCH 跨企业
        mockMvc.perform(patch("/api/v1/hr/jobs/" + jobId + "/status")
                        .headers(hrHeaders(OTHER_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(mapOf("action", "PUBLISH", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
        // DELETE 跨企业
        mockMvc.perform(delete("/api/v1/hr/jobs/" + jobId + "?version=0")
                        .headers(hrHeaders(OTHER_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));

        // 数据库内容不变
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, status, deleted_at FROM job_post WHERE id = ?", jobId);
        assertEquals("Java后端工程师", row.get("title"));
        assertEquals("DRAFT", row.get("status"));
        assertNull(row.get("deleted_at"));
    }

    // ==================== 列表 ====================

    /**
     * 列表只返回本企业岗位（companyId 隔离）
     */
    @Test
    void listByCompany() throws Exception {
        insertJob(TEST_COMPANY_ID);
        insertJob(TEST_COMPANY_ID);
        insertJob(OTHER_COMPANY_ID);
        // 创建人姓名：stub 批量查询（insertJob 固定 created_by=1）
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.singletonList(user(1L, "测试HR"))));

        mockMvc.perform(get("/api/v1/hr/jobs").headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].salary.negotiable").value(false))
                .andExpect(jsonPath("$.data.list[0].createdBy").value(1))
                .andExpect(jsonPath("$.data.list[0].createdByName").value("测试HR"));
    }

    /**
     * 详情返回创建人（createdBy + createdByName，姓名走列表同款批量查询）
     */
    @Test
    void getHrJobDetail_returnsCreator() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.singletonList(user(1L, "测试HR"))));

        mockMvc.perform(get("/api/v1/hr/jobs/" + jobId).headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.createdBy").value(1))
                .andExpect(jsonPath("$.data.createdByName").value("测试HR"));
    }

    /**
     * 用户服务不可用（batch 抛异常）→ 列表仍 200，创建人姓名降级 "用户"+id，且只发生一次批量调用（无 N+1 单查）
     */
    @Test
    void list_creatorNameDegradedWhenUserServiceFails() throws Exception {
        insertJob(TEST_COMPANY_ID);
        when(userFeignClient.batchUsers(anyString())).thenThrow(new RuntimeException("lingxi-user 不可用"));

        mockMvc.perform(get("/api/v1/hr/jobs").headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].createdBy").value(1))
                .andExpect(jsonPath("$.data.list[0].createdByName").value("用户1"));
        // 降级路径也只调用一次批量查询，不逐 id 单查放大请求
        verify(userFeignClient, times(1)).batchUsers(anyString());
    }

    /**
     * 分页边界收敛：page<1/size>50 被收敛不报错
     */
    @Test
    void paginationBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/hr/jobs?page=0&size=100").headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    // ==================== 城市字典 ====================

    /**
     * HR 城市选项：返回全部启用城市（110100/910101），不含停用城市（910102）
     */
    @Test
    void cityOptions_returnsEnabledOnly() throws Exception {
        mockMvc.perform(get("/api/v1/hr/jobs/options").headers(hrHeaders(TEST_COMPANY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cities[?(@.code == '110100')]").exists())
                .andExpect(jsonPath("$.data.cities[?(@.code == '910101')]").exists())
                .andExpect(jsonPath("$.data.cities[?(@.code == '910102')]").doesNotExist());
    }

    /**
     * 创建岗位：城市编码不在字典 → 2107
     */
    @Test
    void create_cityNotFound() throws Exception {
        Map<String, Object> body = createBody();
        body.put("cityCode", "999999");
        body.put("cityName", "不存在");

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2107));
    }

    /**
     * 创建岗位：城市已停用 → 2108
     */
    @Test
    void create_cityDisabled() throws Exception {
        Map<String, Object> body = createBody();
        body.put("cityCode", "910102");
        body.put("cityName", "测试停用城");

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2108));
    }

    /**
     * 编辑岗位：改为字典不存在的城市 → 2107
     */
    @Test
    void update_cityNotFound() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("cityCode", "999999");
        body.put("version", 0);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2107));
    }

    /**
     * 编辑岗位：改为停用城市 → 2108
     */
    @Test
    void update_cityDisabled() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        Map<String, Object> body = baseFields();
        body.put("cityCode", "910102");
        body.put("version", 0);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2108));
    }

    /**
     * 创建岗位：请求传假 cityName 不生效，落库 city_name 为字典名称（cityCode 唯一真值）
     */
    @Test
    void create_ignoresCityName() throws Exception {
        Map<String, Object> body = createBody();
        body.put("cityCode", "910101");
        body.put("cityName", "假城市名");

        mockMvc.perform(post("/api/v1/hr/jobs")
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String cityName = jdbcTemplate.queryForObject(
                "SELECT city_name FROM job_post WHERE company_id = ? ORDER BY id DESC LIMIT 1",
                String.class, TEST_COMPANY_ID);
        assertEquals("测试城A", cityName);
    }

    /**
     * 停用城市的历史岗位：编辑不改城市（cityCode 与原值相同）→ 放行且回填字典名称
     */
    @Test
    void update_unchangedDisabledCityAllowed() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910102', city_name = '旧名' WHERE id = ?", jobId);

        Map<String, Object> body = baseFields();
        body.put("cityCode", "910102");
        body.put("cityName", null);   // 新前端可不传 cityName
        body.put("version", 0);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value(1));

        // 落库 city_name 回填字典名称（910102 → 测试停用城）
        String cityName = jdbcTemplate.queryForObject(
                "SELECT city_name FROM job_post WHERE id = ?", String.class, jobId);
        assertEquals("测试停用城", cityName);
    }

    /**
     * 停用城市的历史岗位：编辑改为另一停用城市 → 2108
     */
    @Test
    void update_changedToDisabledCityRejected() throws Exception {
        Long jobId = insertJob(TEST_COMPANY_ID);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910102' WHERE id = ?", jobId);

        Map<String, Object> body = baseFields();
        body.put("cityCode", "910103");
        body.put("version", 0);

        mockMvc.perform(put("/api/v1/hr/jobs/" + jobId)
                        .headers(hrHeaders(TEST_COMPANY_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2108));
    }

    // ==================== 辅助方法 ====================

    /**
     * HR 请求头（模拟网关注入）
     */
    private HttpHeaders hrHeaders(long companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "HR");
        headers.set("X-Company-Id", String.valueOf(companyId));
        return headers;
    }

    /**
     * HR 但无企业ID 的请求头
     */
    private HttpHeaders hrNoCompanyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "HR");
        return headers;
    }

    /**
     * 非 HR 角色的请求头
     */
    private HttpHeaders candidateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "CANDIDATE");
        headers.set("X-Company-Id", String.valueOf(TEST_COMPANY_ID));
        return headers;
    }

    /**
     * 用户信息 DTO（stub 批量查询用）
     */
    private UserInfoDTO user(long id, String name) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    /**
     * 插入一个已发布可用的测试岗位（DRAFT + 画像已确认 + 完整发布字段）
     */
    private Long insertJob(long companyId) {
        return insertJob(companyId, "DRAFT", null, true, 2);
    }

    /**
     * 插入测试岗位（可定制状态/关闭原因/画像确认状态/总HC），同时插入画像
     */
    private Long insertJob(long companyId, String status, String closeReason, boolean profileConfirmed, int totalHc) {
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
            ps.setString(5, "110100");
            ps.setString(6, "北京");
            ps.setInt(7, 2);
            ps.setString(8, "BACHELOR");
            ps.setLong(9, 100000L);
            ps.setLong(10, 200000L);
            ps.setString(11, "CNY");
            ps.setString(12, "MONTH");
            ps.setInt(13, 12);
            ps.setInt(14, 0);
            ps.setInt(15, totalHc);
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
        Long jobId = keyHolder.getKey().longValue();

        // 插入画像（version=1，可指定是否已确认）
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_profile (company_id, job_id, job_type, core_skills, soft_skills, "
                            + "profile_source, version, confirmed_by, confirmed_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'MANUAL', 1, ?, ?)");
            ps.setLong(1, companyId);
            ps.setLong(2, jobId);
            ps.setString(3, "JAVA_BACKEND");
            ps.setString(4, "[{\"name\":\"Java\",\"level\":\"3\",\"required\":true}]");
            ps.setString(5, "[{\"name\":\"沟通\",\"importance\":\"3\"}]");
            ps.setInt(6, 1001);
            if (profileConfirmed) {
                ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                ps.setNull(7, Types.TIMESTAMP);
            }
            return ps;
        });
        return jobId;
    }

    /**
     * 岗位基础字段（不含画像三件套与 version）
     */
    private Map<String, Object> baseFields() {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Java后端工程师");
        body.put("industryGroupCode", "IT");
        body.put("industryCode", "IT");
        body.put("cityCode", "110100");
        body.put("cityName", "北京");
        body.put("minExperienceYears", 2);
        body.put("educationRequirement", "BACHELOR");
        body.put("salary", salaryMap());
        body.put("totalHc", 2);
        body.put("jdText", "负责后端服务开发与维护");
        return body;
    }

    /**
     * 创建请求体（基础字段 + 画像 + profileConfirmed=true）
     */
    private Map<String, Object> createBody() {
        Map<String, Object> body = baseFields();
        body.put("profile", profileMap());
        body.put("profileConfirmed", true);
        return body;
    }

    /**
     * 薪资信息
     */
    private Map<String, Object> salaryMap() {
        Map<String, Object> salary = new HashMap<>();
        salary.put("minAmount", 100000L);
        salary.put("maxAmount", 200000L);
        salary.put("currency", "CNY");
        salary.put("period", "MONTH");
        salary.put("months", 12);
        salary.put("negotiable", false);
        return salary;
    }

    /**
     * 岗位画像（技能结构对齐 JobRequirementResponse）
     */
    private Map<String, Object> profileMap() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("jobType", "JAVA_BACKEND");
        profile.put("profileSource", "MANUAL");
        List<Map<String, Object>> coreSkills = new ArrayList<>();
        coreSkills.add(skillMap("Java"));
        profile.put("coreSkills", coreSkills);
        return profile;
    }

    /**
     * 单个核心技能项（对齐 JobRequirementResponse.CoreSkill）
     */
    private Map<String, Object> skillMap(String name) {
        Map<String, Object> skill = new HashMap<>();
        skill.put("name", name);
        skill.put("level", "3");
        skill.put("required", true);
        return skill;
    }

    /**
     * 命中广告的核心技能画像（技能标签清洗测试用）
     */
    private Map<String, Object> adProfileMap() {
        Map<String, Object> profile = profileMap();
        profile.put("coreSkills", Collections.singletonList(skillMap("加微信13800000001")));
        return profile;
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

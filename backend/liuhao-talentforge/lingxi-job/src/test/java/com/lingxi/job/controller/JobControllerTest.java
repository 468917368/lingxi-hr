package com.lingxi.job.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.UserFeignClient;
import com.lingxi.job.feign.dto.UserCompanyDTO;
import com.lingxi.job.feign.dto.UserInfoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端岗位发现接口测试（阶段3）
 * <p>模拟网关注入 X-User-Id/X-User-Role 头（CANDIDATE），真实连接本地 MySQL，
 * 前置插入已发布岗位，AfterEach 清理，覆盖搜索/选项/详情/鉴权。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class JobControllerTest {

    /** 测试专用企业ID，避免与 HR 测试（999999/888888）冲突 */
    private static final long TEST_COMPANY_ID = 777777L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                + "('910104', '测试城B', 1)");
    }

    /**
     * 每次测试后清理本测试插入的岗位、画像与测试城市字典行
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
        // 只清理测试专用城市（91010x），不触碰真实 8 城
        jdbcTemplate.update("DELETE FROM job_city_dict WHERE code LIKE '91010%'");
    }

    // ==================== 搜索 ====================

    /**
     * 默认无筛选：只返回 PUBLISHED，PAUSED/CLOSED 不进列表，且返回 skillTags
     */
    @Test
    void search_publishedOnly() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        insertJob(TEST_COMPANY_ID, "PAUSED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        insertJob(TEST_COMPANY_ID, "CLOSED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].skillTags[0]").value("Java"));
    }

    /**
     * 空结果：返回 HTTP 200 + total=0
     */
    @Test
    void search_empty() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /**
     * keyword 命中 title
     */
    @Test
    void search_keyword() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET title = '高级前端工程师' WHERE company_id = ?", TEST_COMPANY_ID);
        Long lastId = jdbcTemplate.queryForObject(
                "SELECT id FROM job_post WHERE company_id = ? ORDER BY id DESC LIMIT 1", Long.class, TEST_COMPANY_ID);
        jdbcTemplate.update("UPDATE job_post SET title = '【TESTJOB】Java后端工程师' WHERE id = ?", lastId);

        mockMvc.perform(get("/api/v1/jobs?keyword=【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * keyword 通配符转义：_ 按字面匹配（转义前 _ 当任意单字符会命中 AXB）
     * <p>岗位A 标题含字面 A_B，岗位B 标题含 AXB：搜 "A_B" 转义后只命中岗位A。</p>
     */
    @Test
    void search_keywordEscapesUnderscore() throws Exception {
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        Long bId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET title = '前端A_B工程师' WHERE id = ?", aId);
        jdbcTemplate.update("UPDATE job_post SET title = '前端AXB工程师' WHERE id = ?", bId);

        mockMvc.perform(get("/api/v1/jobs?keyword=A_B").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId));
    }

    /**
     * keyword 通配符转义：% 按字面匹配（转义前 % 当任意字符串会命中 100X）
     * <p>岗位A 标题含字面 100%，岗位B 标题含 100X：搜 "100%" 转义后只命中岗位A。</p>
     */
    @Test
    void search_keywordEscapesPercent() throws Exception {
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        Long bId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET title = '绩效100%专项' WHERE id = ?", aId);
        jdbcTemplate.update("UPDATE job_post SET title = '绩效100X专项' WHERE id = ?", bId);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "100%").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId));
    }

    /**
     * keyword 通配符转义：= 是转义符自身，需 =→== 转义
     * <p>岗位A 标题含字面 A=B，岗位B 标题含 AB：若 = 不转义，SQL 里 "=B" 会被当转义序列（=B→B），
     * 模式退化为 %AB% 命中岗位B；正确转义后只命中岗位A。</p>
     */
    @Test
    void search_keywordEscapesEqual() throws Exception {
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        Long bId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET title = '前端A=B工程师' WHERE id = ?", aId);
        jdbcTemplate.update("UPDATE job_post SET title = '前端AB工程师' WHERE id = ?", bId);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "A=B").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId));
    }

    /**
     * keyword 反斜杠是普通字符（ESCAPE '=' 下 \ 非转义符，无需处理）
     * <p>岗位A 标题含字面 C:\tmp：搜 "C:\tmp" 按字面命中岗位A。</p>
     */
    @Test
    void search_keywordBackslashLiteral() throws Exception {
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET title = '路径C:\\\\tmp' WHERE id = ?", aId);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "C:\\tmp").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId));
    }

    /**
     * 薪资区间相交：非面议岗位区间与用户区间相交 → 命中
     */
    @Test
    void search_salaryFilter() throws Exception {
        // 岗位薪资 100000~200000，用户区间 150000~250000 → 相交
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        // 岗位薪资 300000~400000，用户区间 150000~250000 → 不相交
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 300000L, 400000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?salaryMin=150000&salaryMax=250000").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * 只传 salaryMin：面议 + 达标岗位均命中
     */
    @Test
    void search_salaryMinOnly() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 1, 0L, 0L, 2, "BACHELOR", 0, false); // 面议

        mockMvc.perform(get("/api/v1/jobs?salaryMin=150000").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    /**
     * 面议岗位金额为 null 仍命中（验证薪资 OR 结构）
     */
    @Test
    void search_negotiableWithNullSalary() throws Exception {
        // 面议岗位金额真实插入 NULL（验证薪资 OR 结构不把 NULL 金额面议岗位过滤掉）
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 1, null, null, 2, "BACHELOR", 0, false);
        // 非面议但不相交
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 300000L, 400000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?salaryMin=150000&salaryMax=250000").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * 经验硬过滤：岗位最低经验超过用户可接受上限 → 被过滤
     */
    @Test
    void search_experienceFilter() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 5, "BACHELOR", 0, false);
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 1, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?experienceMax=3").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * salaryMin > salaryMax → HTTP 200 + code 400
     */
    @Test
    void search_paramInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/jobs?salaryMin=200000&salaryMax=100000").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * salaryMin < 0 → HTTP 200 + code 400
     */
    @Test
    void search_salaryNegative() throws Exception {
        mockMvc.perform(get("/api/v1/jobs?salaryMin=-1").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * page 越界 / size 越界 → HTTP 200 + code 400
     */
    @Test
    void search_pageBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/jobs?page=0").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/jobs?page=101").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/jobs?size=0").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/jobs?size=51").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * sortBy 非法 → HTTP 200 + code 400
     */
    @Test
    void search_sortByInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/jobs?sortBy=HACK").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * LATEST 排序：最新发布在前
     */
    @Test
    void search_sortLatest() throws Exception {
        Long oldId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 10, false);
        Long newId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?sortBy=LATEST").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].jobId").value(newId))
                .andExpect(jsonPath("$.data.list[1].jobId").value(oldId));
    }

    /**
     * 阶段6.3：LATEST 轻量路径保留筛选，且 recommendScore 恒为 0（不计算推荐分 CASE）
     * <p>TDD：实现前当前 LATEST 走原推荐 SQL，recommendScore 为 freshness 分（非 0），预期失败。
     * keyword=【TESTJOB】隔离本测试岗位（本地库含阶段联调业务岗位，按既有测试惯例隔离）。</p>
     */
    @Test
    void search_latestKeepsFilterAndReturnsZeroScore() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false); // IT 北京
        Long gameId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET industry_group_code = 'GAME', industry_code = 'GAME', "
                + "city_code = '310000', city_name = '上海' WHERE id = ?", gameId);

        mockMvc.perform(get("/api/v1/jobs")
                        .param("sortBy", "LATEST")
                        .param("industryGroupCode", "IT")
                        .param("keyword", "【TESTJOB】")
                        .headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(0));
    }

    /**
     * 阶段6.3：非 CANDIDATE（HR）无筛选 RECOMMENDED → 降级最新排序（轻量查询），recommendScore 恒 0
     * <p>本地库含阶段联调业务岗位，故不强断言测试岗位相对顺序（业务岗位发布时间更晚会排前）；
     * 断言测试岗位存在且全列表 recommendScore=0（降级路径不计算推荐分）。</p>
     */
    @Test
    void search_recommendedWithoutProfileFallsBackToLatest() throws Exception {
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?sortBy=RECOMMENDED").headers(hrHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(0))
                // 整个列表 recommendScore 均为 0（轻量路径不计算推荐分 CASE）
                .andExpect(jsonPath("$.data.list[*].recommendScore", everyItem(is(0))));
    }

    /**
     * 阶段6.3：LATEST 保持 published_at DESC, id DESC 稳定排序（相同 published_at 时较大 ID 在前）
     */
    @Test
    void search_latestDoesNotChangePublishedAtThenIdOrder() throws Exception {
        Long firstId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        Long secondId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        // 相同 published_at → 触发 id DESC 兜底
        jdbcTemplate.update("UPDATE job_post SET published_at = ? WHERE id IN (?, ?)",
                Timestamp.valueOf("2026-01-01 00:00:00"), firstId, secondId);

        mockMvc.perform(get("/api/v1/jobs?sortBy=LATEST").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].jobId").value(secondId))
                .andExpect(jsonPath("$.data.list[1].jobId").value(firstId))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(0));
    }

    /**
     * SALARY_DESC 排序：高薪在前，面议靠后
     */
    @Test
    void search_sortSalaryDesc() throws Exception {
        Long lowId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        Long highId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 300000L, 400000L, 2, "BACHELOR", 0, false);
        Long negotiableId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 1, 0L, 0L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?sortBy=SALARY_DESC").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list[0].jobId").value(highId))
                .andExpect(jsonPath("$.data.list[1].jobId").value(lowId))
                .andExpect(jsonPath("$.data.list[2].jobId").value(negotiableId));
    }

    /**
     * 推荐分：面议岗位金额非空仍只 12 分（面议分支最高优先，不被 25/18 抢占）
     * <p>面议岗位：skillScore=0 + salaryScore=12 + exp=0 + edu=0 + freshness=20(当天) = 32</p>
     */
    @Test
    void search_negotiableScore12() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 1, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?salaryMin=100000&salaryMax=200000").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(id))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(32));
    }

    /**
     * 只传一级行业：命中该一级行业下岗位
     */
    @Test
    void search_industryGroupOnly() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false); // IT
        Long gameId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET industry_group_code = 'GAME', industry_code = 'GAME' WHERE id = ?", gameId);

        mockMvc.perform(get("/api/v1/jobs?industryGroupCode=IT").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * 一级行业与具体行业不一致 → HTTP 200 + code 400
     */
    @Test
    void search_industryMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/jobs?industryGroupCode=IT&industryCode=GAME").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * skillTags 边界：去重后 >10 → 400；空标签 → 400；11 重复标签去重后 1 个 → 通过
     */
    @Test
    void search_skillTagsBoundary() throws Exception {
        // 去重后 11 个不同标签 → 400
        String eleven = "?skillTags=t1&skillTags=t2&skillTags=t3&skillTags=t4&skillTags=t5"
                + "&skillTags=t6&skillTags=t7&skillTags=t8&skillTags=t9&skillTags=t10&skillTags=t11";
        mockMvc.perform(get("/api/v1/jobs" + eleven).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        // 空白标签（trim 后为空）→ 400
        mockMvc.perform(get("/api/v1/jobs?skillTags=").param("skillTags", "  ").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        // 11 个重复标签去重后仅 1 个 → 通过（校验去重后数量）
        StringBuilder dup = new StringBuilder("/api/v1/jobs?skillTags=Java");
        for (int i = 0; i < 10; i++) {
            dup.append("&skillTags=Java");
        }
        mockMvc.perform(get(dup.toString()).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * skillTags 命中 vs 完全不命中
     */
    @Test
    void search_skillTagsHitMiss() throws Exception {
        // 默认 core_skills 含 Java/Spring
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?skillTags=Java").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/jobs?skillTags=Kotlin").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /**
     * 学历边界：用户 NONE 仅匹配学历不限岗位；DOCTOR 匹配所有
     */
    @Test
    void search_educationBoundary() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "NONE", 0, false);
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        // NONE(level=0)：仅 level≤0 的岗位（NONE）
        mockMvc.perform(get("/api/v1/jobs?education=NONE").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        // DOCTOR(level=6)：所有学历都满足
        mockMvc.perform(get("/api/v1/jobs?education=DOCTOR").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    /**
     * 软删除的 PUBLISHED 岗位不进列表
     */
    @Test
    void search_softDeletedExcluded() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, true);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /**
     * 默认无筛选：仍返回 skillTags（skillTagsRaw 解析填充后清空）
     */
    @Test
    void search_defaultReturnsSkillTags() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].skillTags").isArray())
                .andExpect(jsonPath("$.data.list[0].skillTags[0]").value("Java"))
                .andExpect(jsonPath("$.data.list[0].skillTags[1]").value("Spring"));
    }

    /**
     * 只传 salaryMax：岗位最低薪资 ≤ salaryMax 即命中（面议+达标岗位均命中）
     */
    @Test
    void search_salaryMaxOnly() throws Exception {
        // 岗位A [10k,20k]，岗位B [30k,40k]，用户 salaryMax=250000
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 300000L, 400000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?salaryMax=250000").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /**
     * 推荐分相同按 publishedAt DESC, id DESC 稳定排序（RECOMMENDED 排序的稳定性回归）
     */
    @Test
    void search_sortStable() throws Exception {
        // 两岗位同字段、无筛选 → 推荐分相同（freshness=20）
        Long firstId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        Long secondId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        // 关键：把两者 published_at 设为完全相同（insertJob 用 Java now() 纳秒精度，天然不同）
        // → 推荐分相同 且 published_at 相同 → 触发 ORDER BY 的 id DESC 兜底分支
        jdbcTemplate.update("UPDATE job_post SET published_at = ? WHERE id IN (?, ?)",
                Timestamp.valueOf("2026-01-01 00:00:00"), firstId, secondId);

        mockMvc.perform(get("/api/v1/jobs?sortBy=RECOMMENDED").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                // 推荐分相同 + published_at 相同 → 按 id DESC 稳定排序（兜底分支），较大 ID 在前
                .andExpect(jsonPath("$.data.list[0].jobId").value(secondId))
                .andExpect(jsonPath("$.data.list[1].jobId").value(firstId));
    }

    /**
     * salaryScore 25/18/0 分档：岗位包含用户区间=25、区间相交=18、未传薪资=0
     */
    @Test
    void search_salaryScoreTiers() throws Exception {
        // 岗位A [10k,30k] 包含用户 [15k,25k] → 25 分
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 300000L, 2, "BACHELOR", 0, false);
        // 岗位B [20k,35k] 与用户 [15k,25k] 相交（非包含）→ 18 分
        Long bId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 200000L, 350000L, 2, "BACHELOR", 0, false);

        // 传薪资：25 vs 18，A 在前；总分 = salaryScore + freshness(20)
        mockMvc.perform(get("/api/v1/jobs?salaryMin=150000&salaryMax=250000").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(45))   // 25 + 20
                .andExpect(jsonPath("$.data.list[1].jobId").value(bId))
                .andExpect(jsonPath("$.data.list[1].recommendScore").value(38));  // 18 + 20

        // 不传薪资：salaryScore=0，仅 freshness=20
        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(20));
    }

    /**
     * freshness 分档：2/6/14/29 天前发布 → 20/15/10/5 分
     */
    @Test
    void search_freshnessTiers() throws Exception {
        Long d2 = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 2, false);
        Long d6 = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 6, false);
        Long d14 = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 14, false);
        Long d29 = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 29, false);

        mockMvc.perform(get("/api/v1/jobs?sortBy=RECOMMENDED").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(4))
                // 分数降序：2天=20 → 6天=15 → 14天=10 → 29天=5
                .andExpect(jsonPath("$.data.list[0].jobId").value(d2))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(20))
                .andExpect(jsonPath("$.data.list[1].jobId").value(d6))
                .andExpect(jsonPath("$.data.list[1].recommendScore").value(15))
                .andExpect(jsonPath("$.data.list[2].jobId").value(d14))
                .andExpect(jsonPath("$.data.list[2].recommendScore").value(10))
                .andExpect(jsonPath("$.data.list[3].jobId").value(d29))
                .andExpect(jsonPath("$.data.list[3].recommendScore").value(5));
    }

    /**
     * 综合推荐分：skillTags + 薪资 + 经验 + 学历 + freshness 五分量求和
     * <p>岗位（Java 技能、[10k,30k]、2年、BACHELOR、当天）；用户（skillTags=Java、[15k,25k]、expMax=3、BACHELOR）
     * → 30+25+12+10+20 = 97。</p>
     */
    @Test
    void search_recommendedScore() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 300000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs?skillTags=Java&salaryMin=150000&salaryMax=250000&experienceMax=3&education=BACHELOR")
                        .headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(97));
    }

    // ==================== 画像推荐模式（阶段3.5） ====================

    /**
     * CANDIDATE 无筛选+RECOMMENDED → 触发画像模式，Feign 降级（本地无 lingxi-user）后仍正常返回已发布岗位
     */
    @Test
    void search_profileModeSmoke() throws Exception {
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    /**
     * 带筛选（keyword）→ 不进画像模式，走现有条件推荐，total 精确命中本测试岗位
     */
    @Test
    void search_filterNotTriggerProfile() throws Exception {
        Long aId = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId));
    }

    // ==================== 选项 ====================

    /**
     * 静态选项：10 行业 + 3 排序 + 城市数组（读库去重，至少含本测试插入的已发布岗位城市）+ 技能数组（画像 coreSkills 提取）
     */
    @Test
    void jobOptions_returns10Industries() throws Exception {
        // 前置插入一条已发布岗位（城市固定北京 110100，默认画像 coreSkills 含 Java/Spring），保证 cities/skills 至少一条
        insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.industries.length()").value(10))
                .andExpect(jsonPath("$.data.sortOptions.length()").value(3))
                .andExpect(jsonPath("$.data.cities").isArray())
                .andExpect(jsonPath("$.data.cities[?(@.code == '110100')]").exists())
                .andExpect(jsonPath("$.data.skills").isArray())
                .andExpect(jsonPath("$.data.skills[?(@ == 'Java')]").exists())
                .andExpect(jsonPath("$.data.skills[?(@ == 'Spring')]").exists());
    }

    /**
     * /jobs/options 进静态方法而非详情（不返回 2101）
     */
    @Test
    void options_staticRoute() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.industries").isArray());
    }

    /**
     * HR 角色可访问 /jobs/options（HR 创建/编辑岗位行业下拉复用；发现类接口对任意已登录角色开放，仅收藏限 CANDIDATE）
     */
    @Test
    void options_hrRoleAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/options").headers(hrHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.industries.length()").value(10))
                .andExpect(jsonPath("$.data.sortOptions.length()").value(3));
    }

    /**
     * C 端城市选项：仅返回"存在已发布且未删除岗位"的启用城市
     * 910101（已发布岗位）进 cities；910102（停用城市，即使有已发布岗位）不进
     */
    @Test
    void options_cities_onlyPublishedEnabled() throws Exception {
        Long enabled = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910101', city_name = '假名' WHERE id = ?", enabled);
        // 停用城市也有已发布岗位：字典 status=0 → JOIN 过滤掉
        Long disabled = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910102', city_name = '假名' WHERE id = ?", disabled);

        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cities[?(@.code == '910101')]").exists())
                .andExpect(jsonPath("$.data.cities[?(@.code == '910102')]").doesNotExist());
    }

    /**
     * C 端城市选项：仅 DRAFT/CLOSED 岗位的城市（910104）不进 cities
     */
    @Test
    void options_cities_excludeDraftClosed() throws Exception {
        Long draft = insertJob(TEST_COMPANY_ID, "DRAFT", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910104' WHERE id = ?", draft);
        Long closed = insertJob(TEST_COMPANY_ID, "CLOSED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910104' WHERE id = ?", closed);

        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cities[?(@.code == '910104')]").doesNotExist());
    }

    /**
     * C 端城市选项：软删除的已发布岗位城市（910104）不进 cities
     */
    @Test
    void options_cities_excludeSoftDeleted() throws Exception {
        Long deleted = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, true);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910104' WHERE id = ?", deleted);

        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cities[?(@.code == '910104')]").doesNotExist());
    }

    /**
     * C 端城市选项：同一城市多岗位去重只返回一条，名称取字典（不信任 job_post.city_name）
     * <p>去重由 SELECT DISTINCT 保证；此处用 filter 内多条件断言"名称取字典"——
     * 注意：Jayway filter 后链式 `.length()`/`[0].name` 不可靠（length 返回外层数组长度），故不用。</p>
     */
    @Test
    void options_cities_dedupAndDictName() throws Exception {
        // 同城 910101 两条已发布岗位，job_post.city_name 均写假名"假名"（字典名为"测试城A"）
        Long a = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910101', city_name = '假名' WHERE id = ?", a);
        Long b = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '910101', city_name = '假名' WHERE id = ?", b);

        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 名称取字典（"测试城A"）而非 job_post.city_name（"假名"）
                .andExpect(jsonPath("$.data.cities[?(@.code == '910101' && @.name == '测试城A')]").exists())
                .andExpect(jsonPath("$.data.cities[?(@.code == '910101' && @.name == '假名')]").doesNotExist());
    }

    /**
     * C 端城市选项：存量省级码 110000 迁移为市辖区码 110100 后不丢城市
     * （迁移仅限本测试企业，等价 init_city_dict.sql 第三段；迁移前 110000 不在字典故 JOIN 不到）
     */
    @Test
    void options_cities_afterLegacyCodeMigration() throws Exception {
        Long legacy = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET city_code = '110000' WHERE id = ?", legacy);

        // 迁移前：110000 不在字典 → 不出现在 cities
        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cities[?(@.code == '110000')]").doesNotExist());

        // 执行存量迁移（限本测试企业，不动真实存量岗位）
        jdbcTemplate.update("UPDATE job_post SET city_code = '110100' WHERE city_code = '110000' AND company_id = ?",
                TEST_COMPANY_ID);

        mockMvc.perform(get("/api/v1/jobs/options").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cities[?(@.code == '110100')]").exists())
                .andExpect(jsonPath("$.data.cities[?(@.code == '110000')]").doesNotExist());
    }

    // ==================== 企业名与招聘负责人 ====================

    /**
     * 搜索列表：企业名与招聘负责人由创建人批量信息回填；内部字段 companyId/createdBy 不序列化
     */
    @Test
    void search_returnsCompanyAndCreatorName() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET created_by = 1001 WHERE id = ?", id);
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.singletonList(testCreatorUser())));

        mockMvc.perform(get("/api/v1/jobs").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].companyName").value("测试企业"))
                .andExpect(jsonPath("$.data.list[0].creatorName").value("测试招聘负责人"))
                .andExpect(jsonPath("$.data.list[0].companyId").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].createdBy").doesNotExist());
        verify(userFeignClient, times(1)).batchUsers(anyString());
    }

    /**
     * LATEST 轻量查询路径同样回填企业名与招聘负责人
     */
    @Test
    void latestSearch_returnsCompanyAndCreatorName() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET created_by = 1001 WHERE id = ?", id);
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.singletonList(testCreatorUser())));

        mockMvc.perform(get("/api/v1/jobs").param("sortBy", "LATEST").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].companyName").value("测试企业"))
                .andExpect(jsonPath("$.data.list[0].creatorName").value("测试招聘负责人"));
    }

    /**
     * 详情：企业名与招聘负责人回填
     */
    @Test
    void detail_returnsCompanyAndCreatorName() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET created_by = 1001 WHERE id = ?", id);
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.singletonList(testCreatorUser())));

        mockMvc.perform(get("/api/v1/jobs/" + id).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.companyName").value("测试企业"))
                .andExpect(jsonPath("$.data.creatorName").value("测试招聘负责人"));
    }

    /**
     * 创建人当前企业与岗位 company_id 不一致 → 企业名降级"招聘企业"，负责人姓名仍为真实姓名
     */
    @Test
    void search_companyMismatchFallsBackToGenericCompany() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET created_by = 1001 WHERE id = ?", id);
        // 用户当前企业（666666）与岗位企业（TEST_COMPANY_ID=777777）不一致
        UserInfoDTO user = testCreatorUser();
        user.getCompany().setId(666666L);
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.singletonList(user)));

        mockMvc.perform(get("/api/v1/jobs").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].companyName").value("招聘企业"))
                .andExpect(jsonPath("$.data.list[0].creatorName").value("测试招聘负责人"));
    }

    /**
     * 用户服务不可用（batch 抛异常）→ 列表仍 200，降级为通用文案，仅一次批量调用
     */
    @Test
    void search_userFeignUnavailableDoesNotBlock() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        jdbcTemplate.update("UPDATE job_post SET created_by = 1001 WHERE id = ?", id);
        when(userFeignClient.batchUsers(anyString())).thenThrow(new RuntimeException("lingxi-user 不可用"));

        mockMvc.perform(get("/api/v1/jobs").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].companyName").value("招聘企业"))
                .andExpect(jsonPath("$.data.list[0].creatorName").value("招聘负责人"));
        verify(userFeignClient, times(1)).batchUsers(anyString());
    }

    /**
     * 用户服务返回空数据（创建人缺失/无企业归属）→ 详情仍 200，通用文案
     * <p>注：job_post.created_by 为 NOT NULL，无法构造 NULL 历史岗位；"创建人缺失"以 batch 返回空列表模拟。</p>
     */
    @Test
    void detail_userMissingFallsBack() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);
        when(userFeignClient.batchUsers(anyString()))
                .thenReturn(Result.success(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/jobs/" + id).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.companyName").value("招聘企业"))
                .andExpect(jsonPath("$.data.creatorName").value("招聘负责人"));
        verify(userFeignClient, times(1)).batchUsers(anyString());
    }

    /**
     * 测试创建人用户（1001/测试招聘负责人，企业=TEST_COMPANY_ID/测试企业）
     */
    private UserInfoDTO testCreatorUser() {
        UserInfoDTO user = new UserInfoDTO();
        user.setId(1001L);
        user.setName("测试招聘负责人");
        UserCompanyDTO company = new UserCompanyDTO();
        company.setId(TEST_COMPANY_ID);
        company.setName("测试企业");
        user.setCompany(company);
        return user;
    }

    // ==================== 详情 ====================

    /**
     * 详情完整字段：companyName=null、profile 不含 hiddenRequirements、画像已确认
     */
    @Test
    void detail_published() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs/" + id).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobId").value(id))
                // 企业名/招聘负责人恒有展示值：未 stub 用户服务 → 通用文案降级
                .andExpect(jsonPath("$.data.companyName").value("招聘企业"))
                .andExpect(jsonPath("$.data.creatorName").value("招聘负责人"))
                .andExpect(jsonPath("$.data.salary.minAmount").value(100000))
                .andExpect(jsonPath("$.data.profile.jobType").value("JAVA_BACKEND"))
                .andExpect(jsonPath("$.data.profile.coreSkills[0].name").value("Java"))
                .andExpect(jsonPath("$.data.profile.profileConfirmed").value(true))
                .andExpect(jsonPath("$.data.profile.hiddenRequirements").doesNotExist());
    }

    /**
     * 详情非 PUBLISHED（CLOSED）→ 2101
     */
    @Test
    void detail_notPublished() throws Exception {
        Long id = insertJob(TEST_COMPANY_ID, "CLOSED", 0, 100000L, 200000L, 2, "BACHELOR", 0, false);

        mockMvc.perform(get("/api/v1/jobs/" + id).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /**
     * 详情不存在 → 2101
     */
    @Test
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/999999999").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101));
    }

    /**
     * 画像缺失：岗位正常返回，profile 对象存在，各字段默认值
     */
    @Test
    void detail_profileMissing() throws Exception {
        Long id = insertJobNoProfile(TEST_COMPANY_ID, "PUBLISHED", 0, 100000L, 200000L, 2, "BACHELOR");

        mockMvc.perform(get("/api/v1/jobs/" + id).headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.profile").exists())
                .andExpect(jsonPath("$.data.profile.jobType").isEmpty())
                .andExpect(jsonPath("$.data.profile.coreSkills").isArray())
                .andExpect(jsonPath("$.data.profile.coreSkills.length()").value(0))
                .andExpect(jsonPath("$.data.profile.softSkills.length()").value(0))
                .andExpect(jsonPath("$.data.profile.industryExperience").isEmpty())
                .andExpect(jsonPath("$.data.profile.interviewFocus.length()").value(0))
                .andExpect(jsonPath("$.data.profile.profileConfirmed").value(false));
    }

    /**
     * 详情非数字 ID → MethodArgumentTypeMismatchException → HTTP 400 + code 400
     */
    @Test
    void detail_nonNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/abc").headers(candidateHeaders()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 鉴权 ====================

    /**
     * 未登录（无 X-User-Id）→ 401（类级 @RequireLogin）
     */
    @Test
    void noLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 辅助方法 ====================

    /**
     * C端请求头（模拟网关注入，CANDIDATE 角色登录即可）
     */
    private HttpHeaders candidateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "CANDIDATE");
        return headers;
    }

    /** 非 CANDIDATE 请求头（降级路径验证用） */
    private HttpHeaders hrHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "2001");
        headers.set("X-User-Role", "HR");
        return headers;
    }

    /**
     * 插入已发布测试岗位（含默认画像：JAVA_BACKEND + Java/Spring 核心技能 + 已确认）
     *
     * @param publishedDaysAgo 发布时间（天数前），0=当天；null 不设 published_at
     * @param deleted          是否软删除
     */
    private Long insertJob(long companyId, String status, int negotiable, Long salaryMin, Long salaryMax,
                           int minExp, String education, Integer publishedDaysAgo, boolean deleted) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, total_hc, jd_text, status, published_at, deleted_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "【TESTJOB】Java后端工程师");
            ps.setString(3, "IT");
            ps.setString(4, "IT");
            ps.setString(5, "110100");
            ps.setString(6, "北京");
            ps.setInt(7, minExp);
            ps.setString(8, education);
            // 面议岗位金额可为 NULL：BIGINT 字段用 setNull 真实插入 NULL，验证 OR 结构不把面议岗位过滤掉
            if (salaryMin == null) {
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(9, salaryMin);
            }
            if (salaryMax == null) {
                ps.setNull(10, Types.BIGINT);
            } else {
                ps.setLong(10, salaryMax);
            }
            ps.setString(11, "CNY");
            ps.setString(12, "MONTH");
            ps.setInt(13, 12);
            ps.setInt(14, negotiable);
            ps.setInt(15, 2);
            ps.setString(16, "负责后端服务开发与维护");
            ps.setString(17, status);
            // published_at 先占位（后续按 publishedDaysAgo 单独 UPDATE）
            if (publishedDaysAgo == null) {
                ps.setNull(18, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(18, Timestamp.valueOf(LocalDateTime.now()));
            }
            if (deleted) {
                ps.setTimestamp(19, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                ps.setNull(19, Types.TIMESTAMP);
            }
            ps.setInt(20, 1);
            return ps;
        }, keyHolder);
        Long jobId = keyHolder.getKey().longValue();

        // 按天数回拨 published_at（freshness 边界内侧，同数据库时间基准避免 NOW() 抖动）
        if (publishedDaysAgo != null && publishedDaysAgo > 0) {
            jdbcTemplate.update(
                    "UPDATE job_post SET published_at = DATE_SUB(NOW(), INTERVAL ? DAY) WHERE id = ?",
                    publishedDaysAgo, jobId);
        }

        // 默认插入已确认画像
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_profile (company_id, job_id, job_type, core_skills, soft_skills, "
                            + "industry_experience, interview_focus, profile_source, version, confirmed_by, confirmed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'MANUAL', 1, ?, ?)");
            ps.setLong(1, companyId);
            ps.setLong(2, jobId);
            ps.setString(3, "JAVA_BACKEND");
            ps.setString(4, "[{\"name\":\"Java\",\"level\":\"3\",\"required\":true},{\"name\":\"Spring\",\"level\":\"3\",\"required\":false}]");
            ps.setString(5, "[{\"name\":\"沟通\",\"importance\":\"3\"}]");
            ps.setString(6, "互联网行业经验");
            ps.setString(7, "[\"Spring事务\",\"并发编程\"]");
            ps.setInt(8, 1001);
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        });
        return jobId;
    }

    /**
     * 插入已发布测试岗位（不含画像，用于画像缺失场景）
     */
    private Long insertJobNoProfile(long companyId, String status, int negotiable, Long salaryMin, Long salaryMax,
                                    int minExp, String education) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, total_hc, jd_text, status, published_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "【TESTJOB】Java后端工程师");
            ps.setString(3, "IT");
            ps.setString(4, "IT");
            ps.setString(5, "110100");
            ps.setString(6, "北京");
            ps.setInt(7, minExp);
            ps.setString(8, education);
            if (salaryMin == null) {
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(9, salaryMin);
            }
            if (salaryMax == null) {
                ps.setNull(10, Types.BIGINT);
            } else {
                ps.setLong(10, salaryMax);
            }
            ps.setString(11, "CNY");
            ps.setString(12, "MONTH");
            ps.setInt(13, 12);
            ps.setInt(14, negotiable);
            ps.setInt(15, 2);
            ps.setString(16, "负责后端服务开发与维护");
            ps.setString(17, status);
            ps.setTimestamp(18, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(19, 1);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}

package com.lingxi.job.service;

import com.lingxi.job.mapper.JobPostMapper;
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
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C端岗位选项 skills 技能标签测试（阶段3.5）
 * <p>直接测 Service/Mapper 层：真实连接本地 MySQL，前置插入已发布/草稿/已删除岗位画像。
 * C 端技能标签覆盖<b>所有企业</b>的已发布岗位（不限定 companyId，与 selectCityOptions 一致），
 * 故断言采用「包含/不包含 + 去空 + 去重计数 + 升序 + LIMIT」语义，兼容库中既有已发布岗位数据。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
class JobOptionsSkillTest {

    /** 测试专用企业ID，独立于其他测试类 */
    private static final long TEST_COMPANY_ID = 777778L;

    @Autowired
    private CandidateJobService candidateJobService;

    @Autowired
    private JobPostMapper jobPostMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 每次测试后清理本测试插入的岗位和画像
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
    }

    // ==================== 用例 ====================

    /**
     * 已发布岗位技能返回 + 空名（null/空白）被过滤：插入独特技能 + 空名技能，返回含独特技能且所有项非空白
     */
    @Test
    void publishedReturnedAndBlankFiltered() {
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false,
                "[{\"name\":\"SkillTest_Pub1\"},{\"name\":null},{\"name\":\"\"},{\"name\":\"   \"}]");

        List<String> skills = candidateJobService.listSkillOptions();
        assertTrue(skills.contains("SkillTest_Pub1"), "已发布岗位技能应返回");
        assertTrue(skills.stream().allMatch(s -> s != null && !s.trim().isEmpty()),
                "null/空白技能名应被过滤，返回项均非空白");
    }

    /**
     * 重复技能去重：两个已发布岗位画像都含独特技能 SkillTest_Dedup，一个含 SkillTest_Other
     * → SkillTest_Dedup 仅出现一次，SkillTest_Other 出现
     */
    @Test
    void dedupAcrossJobs() {
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false,
                "[{\"name\":\"SkillTest_Dedup\",\"level\":\"3\"},{\"name\":\"SkillTest_Other\",\"level\":\"3\"}]");
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false,
                "[{\"name\":\"SkillTest_Dedup\",\"level\":\"2\"}]");

        List<String> skills = candidateJobService.listSkillOptions();
        assertEquals(1, skills.stream().filter("SkillTest_Dedup"::equals).count(),
                "重复技能只应出现一次");
        assertTrue(skills.contains("SkillTest_Other"), "不同技能应均被返回");
    }

    /**
     * 升序排序：返回列表按 utf8mb4_unicode_ci 排序规则升序（SQL 显式 COLLATE 固定，不依赖连接 collation）
     * <p>期望按 MySQL utf8mb4_unicode_ci 实际顺序硬编码（不能按 Java 字典序——二者对大小写混合字符串排序不同，
     * 且 SQL 排序与连接 collation 解耦后必须断言确定行为）。</p>
     */
    @Test
    void sortedAsc() {
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("SkillTest_ZZZ"));
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("SkillTest_AAA"));

        List<String> skills = candidateJobService.listSkillOptions();
        List<String> expected = Arrays.asList(
                "CNVD", "CVE", "Go", "Java", "JavaScript", "Kubernetes", "MySQL", "Python",
                "React", "SkillTest_AAA", "SkillTest_ZZZ", "Spring", "Spring Boot", "SRC",
                "TypeScript", "Vue", "Webpack",
                "产品设计", "内网穿透", "安全攻防", "安全服务", "数据分析", "数据建模", "渗透技术", "用户增长");
        assertEquals(expected, skills, "技能列表应按 utf8mb4_unicode_ci 升序");
    }

    /**
     * 草稿岗位技能不进选项：PUBLISHED 含独特技能 SkillTest_Pub2，DRAFT 含 SkillTest_DraftExcl
     */
    @Test
    void draftExcluded() {
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("SkillTest_Pub2"));
        insertJobWithSkills(TEST_COMPANY_ID, "DRAFT", false, skills("SkillTest_DraftExcl"));

        List<String> skills = candidateJobService.listSkillOptions();
        assertTrue(skills.contains("SkillTest_Pub2"), "已发布岗位技能应返回");
        assertFalse(skills.contains("SkillTest_DraftExcl"), "草稿岗位技能不应返回");
    }

    /**
     * 已删除岗位技能不进选项：PUBLISHED 正常含独特技能 SkillTest_Pub3，PUBLISHED 已软删除含 SkillTest_DeletedExcl
     */
    @Test
    void deletedExcluded() {
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("SkillTest_Pub3"));
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", true, skills("SkillTest_DeletedExcl"));

        List<String> skills = candidateJobService.listSkillOptions();
        assertTrue(skills.contains("SkillTest_Pub3"), "未删除岗位技能应返回");
        assertFalse(skills.contains("SkillTest_DeletedExcl"), "已删除岗位技能不应返回");
    }

    /**
     * LIMIT 控制返回数量：Mapper LIMIT 参数生效；Service 返回数量不超过 SKILL_LIMIT
     */
    @Test
    void limitApplied() {
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("AAA"));
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("BBB"));
        insertJobWithSkills(TEST_COMPANY_ID, "PUBLISHED", false, skills("CCC"));

        assertEquals(2, jobPostMapper.selectSkillOptions(2).size(),
                "LIMIT=2 应只返回 2 条");
        assertTrue(candidateJobService.listSkillOptions().size() <= 100,
                "Service 返回数量不应超过 SKILL_LIMIT");
    }

    // ==================== 辅助 ====================

    private static String skills(String... names) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"name\":\"").append(names[i]).append("\",\"level\":\"3\"}");
        }
        return sb.append("]").toString();
    }

    /**
     * 插入测试岗位 + 指定 core_skills 的画像
     *
     * @param deleted 是否软删除（deleted_at 置当前时间）
     */
    private long insertJobWithSkills(long companyId, String status, boolean deleted, String coreSkillsJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, total_hc, jd_text, status, published_at, deleted_at, created_by) "
                            + "VALUES (?, ?, 'IT', 'IT', '110000', '北京', 2, 'BACHELOR', 100000, 200000, "
                            + "'CNY', 'MONTH', 12, 0, 2, '测试', ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "【SKILLTEST】岗位" + status + deleted);
            ps.setString(3, status);
            if ("DRAFT".equals(status)) {
                ps.setNull(4, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            }
            if (deleted) {
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                ps.setNull(5, Types.TIMESTAMP);
            }
            return ps;
        }, keyHolder);
        long jobId = keyHolder.getKey().longValue();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_profile (company_id, job_id, job_type, core_skills, soft_skills, "
                            + "profile_source, version, confirmed_by, confirmed_at) "
                            + "VALUES (?, ?, 'JAVA_BACKEND', ?, '[]', 'MANUAL', 1, 1001, ?)");
            ps.setLong(1, companyId);
            ps.setLong(2, jobId);
            ps.setString(3, coreSkillsJson);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        });
        return jobId;
    }
}

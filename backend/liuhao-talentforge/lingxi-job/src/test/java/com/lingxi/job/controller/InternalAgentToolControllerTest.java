package com.lingxi.job.controller;

import com.lingxi.job.agent.AgentContextStore;
import com.lingxi.job.agent.AgentRunContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 百宝箱 Agent Tool 内部回调测试
 * <p>覆盖 runToken 校验：无 token 401/过期 401/跨岗位拒绝/正常返回/题库搜索脱敏。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class InternalAgentToolControllerTest {

    private static final long TEST_COMPANY_ID = 777777L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentContextStore agentContextStore;

    /** 已创建的 runToken（AfterEach 清理） */
    private final List<String> createdTokens = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String token : createdTokens) {
            agentContextStore.remove(token);
        }
        createdTokens.clear();
        jdbcTemplate.update("DELETE FROM job_question WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
    }

    // ==================== runToken 校验 ====================

    @Test
    void tool_noToken401() throws Exception {
        mockMvc.perform(get("/internal/agent/jobs/1/requirements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void tool_expiredToken401() throws Exception {
        String token = putContext(TEST_COMPANY_ID, 100L, 200L, "JAVA_BACKEND", LocalDateTime.now().minusSeconds(1));
        mockMvc.perform(get("/internal/agent/jobs/100/requirements").header("X-Run-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void tool_crossCompanyJobMismatch401() throws Exception {
        // runToken 绑定 jobId=100，但请求 jobId=200 → 拒绝
        String token = putContext(TEST_COMPANY_ID, 100L, 200L, "JAVA_BACKEND", LocalDateTime.now().plusSeconds(300));
        mockMvc.perform(get("/internal/agent/jobs/200/requirements").header("X-Run-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== Tool1 岗位画像 ====================

    @Test
    void tool_requirements_success() throws Exception {
        Long jobId = insertJobWithProfile(TEST_COMPANY_ID, "JAVA_BACKEND");
        String token = putContext(TEST_COMPANY_ID, jobId, 200L, "JAVA_BACKEND", LocalDateTime.now().plusSeconds(300));

        mockMvc.perform(get("/internal/agent/jobs/{jobId}/requirements", jobId).header("X-Run-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobType").value("JAVA_BACKEND"))
                .andExpect(jsonPath("$.data.coreSkills[0].name").value("Java"));
    }

    // ==================== Tool2 简历亮点 ====================

    @Test
    void tool_highlights_success() throws Exception {
        AgentRunContext ctx = new AgentRunContext();
        ctx.setApplicationId(200L);
        ctx.setPersonalized(true);
        List<String> highlights = new ArrayList<>();
        highlights.add("负责 Java 后端开发");
        ctx.setResumeHighlights(highlights);
        String token = putContextWith(ctx, LocalDateTime.now().plusSeconds(300));

        mockMvc.perform(get("/internal/agent/applications/200/highlights").header("X-Run-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value("负责 Java 后端开发"));
    }

    @Test
    void tool_lowPersonalized_highlightsEmpty() throws Exception {
        // 低完整度（personalized=false）→ Tool2 必须返回空亮点（跳过简历上下文闭环）
        AgentRunContext ctx = new AgentRunContext();
        ctx.setApplicationId(300L);
        ctx.setPersonalized(false);
        ctx.setResumeHighlights(Arrays.asList("负责 Java 后端开发"));
        String token = putContextWith(ctx, LocalDateTime.now().plusSeconds(300));

        mockMvc.perform(get("/internal/agent/applications/300/highlights").header("X-Run-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ==================== Tool3 题库搜索 ====================

    @Test
    void tool_questionsSearch() throws Exception {
        Long jobId = insertJobWithProfile(TEST_COMPANY_ID, "JAVA_BACKEND");
        insertQuestion(TEST_COMPANY_ID, "JAVA_BACKEND", "BASIC", "MEDIUM", "Java 基础题?", "sha-basic-001",
                "[\"Java\"]", "参考答案-基础");
        insertQuestion(TEST_COMPANY_ID, "JAVA_BACKEND", "PROJECT", "MEDIUM", "项目深挖题?", "sha-project-001",
                "[\"Spring\"]", "参考答案-项目");
        String token = putContext(TEST_COMPANY_ID, jobId, 200L, "JAVA_BACKEND", LocalDateTime.now().plusSeconds(300));

        mockMvc.perform(get("/internal/agent/questions/search")
                        .header("X-Run-Token", token)
                        .param("skillTags", "Java")
                        .param("questionType", "BASIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].questionType").value("BASIC"))
                .andExpect(jsonPath("$.data[0].content").isNotEmpty())
                // 安全边界：不返回 referenceAnswer/id/companyId
                .andExpect(jsonPath("$.data[0].referenceAnswer").doesNotExist())
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].companyId").doesNotExist());
    }

    // ==================== 辅助方法 ====================

    /** 写入未过期的 runToken 上下文 */
    private String putContext(Long companyId, Long jobId, Long appId, String jobType, LocalDateTime expiresAt) {
        AgentRunContext ctx = new AgentRunContext();
        ctx.setCompanyId(companyId);
        ctx.setJobId(jobId);
        ctx.setApplicationId(appId);
        ctx.setJobType(jobType);
        return putContextWith(ctx, expiresAt);
    }

    private String putContextWith(AgentRunContext ctx, LocalDateTime expiresAt) {
        String token = UUID.randomUUID().toString().replace("-", "");
        ctx.setRunToken(token);
        ctx.setExpiresAt(expiresAt);
        agentContextStore.put(token, ctx);
        createdTokens.add(token);
        return token;
    }

    private Long insertJob(long companyId) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, status, created_by) "
                            + "VALUES (?, ?, 'IT', 'IT', '110000', '北京', 2, 'BACHELOR', 'PUBLISHED', 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, "Java后端工程师");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long insertJobWithProfile(long companyId, String jobType) {
        Long jobId = insertJob(companyId);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_profile (company_id, job_id, job_type, core_skills, soft_skills, "
                            + "profile_source, version) VALUES (?, ?, ?, ?, ?, 'MANUAL', 1)");
            ps.setLong(1, companyId);
            ps.setLong(2, jobId);
            ps.setString(3, jobType);
            ps.setString(4, "[{\"name\":\"Java\",\"level\":\"3\",\"required\":true}]");
            ps.setString(5, "[{\"name\":\"沟通\",\"importance\":\"3\"}]");
            return ps;
        });
        return jobId;
    }

    private void insertQuestion(long companyId, String jobType, String questionType, String difficulty,
                                String content, String contentSha256, String skillTags, String referenceAnswer) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_question (company_id, job_type, skill_tags, question_type, difficulty, "
                            + "content, content_sha256, key_points, reference_answer, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')");
            ps.setLong(1, companyId);
            ps.setString(2, jobType);
            ps.setString(3, skillTags);
            ps.setString(4, questionType);
            ps.setString(5, difficulty);
            ps.setString(6, content);
            ps.setString(7, contentSha256);
            ps.setString(8, "考察要点");
            ps.setString(9, referenceAnswer);
            return ps;
        });
    }
}

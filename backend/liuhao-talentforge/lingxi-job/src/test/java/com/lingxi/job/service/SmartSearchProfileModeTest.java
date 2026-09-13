package com.lingxi.job.service;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.dto.CandidateProfileDTO;
import com.lingxi.job.feign.CandidateProfileFeignClient;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端岗位搜索「画像推荐模式」测试（阶段3.5）
 * <p>@MockBean 替换 lingxi-user 画像 Feign，真实连接本地 MySQL，前置插入已发布岗位。
 * 断言策略：画像模式无筛选会返回<b>库中全部已发布岗位</b>，故排序/分数断言依赖本测试
 * 岗位画像命中得分显著高于业务数据（无筛选条件推荐仅 freshness 20 分），
 * 并用 jsonPath 过滤器断言本测试岗位存在性（不过滤回归）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
class SmartSearchProfileModeTest {

    /** 测试专用企业ID，独立于 JobControllerTest(777777)/JobOptionsSkillTest(777778) */
    private static final long TEST_COMPANY_ID = 777779L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 画像 Feign mock：未 stub 默认返回 null（等价 fallback 降级结果，[R9]） */
    @MockBean
    private CandidateProfileFeignClient candidateProfileFeignClient;

    /**
     * 每次测试后清理本测试插入的岗位和画像
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM job_profile WHERE company_id = ?", TEST_COMPANY_ID);
        jdbcTemplate.update("DELETE FROM job_post WHERE company_id = ?", TEST_COMPANY_ID);
    }

    // ==================== 触发条件 ====================

    /**
     * CANDIDATE 无筛选 + 默认 RECOMMENDED → 触发画像模式，Feign 被调用，画像六维满分 100
     */
    @Test
    void profileMode_trigger() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong())).thenReturn(Result.success(fullProfile()));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 六维全命中：25+25+15+15+10+10 = 100，必然置顶
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(100));

        verify(candidateProfileFeignClient).getUserProfile(anyLong());
    }

    /**
     * 画像缺失（HTTP 200 + code=1113 + data=null）→ 显式降级最新排序，不抛异常 [R1]
     * <p>阶段6.3：降级走轻量查询，recommendScore 恒 0（不再计算 freshness 分）。</p>
     */
    @Test
    void profileNotFound_1113_fallbackLatest() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong())).thenReturn(Result.error(1113, "用户画像不存在"));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(0));
    }

    /**
     * Feign 异常降级：fallback 返回 null → Service 识别降级，不抛异常 [R9]
     * <p>阶段6.3：降级走轻量查询，recommendScore 恒 0。</p>
     */
    @Test
    void feignError_fallback() throws Exception {
        // 不 stub：@MockBean 默认返回 null，等价 CandidateProfileFeignFallback 降级结果
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(0));
    }

    /**
     * 非 CANDIDATE（HR）无筛选 → 不进画像模式，Feign 不调用
     */
    @Test
    void nonCandidate_skip() throws Exception {
        insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(hrHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(candidateProfileFeignClient, never()).getUserProfile(anyLong());
    }

    /**
     * 有筛选（keyword）→ 不进画像模式，Feign 不调用 [R8 系列]
     */
    @Test
    void hasFilter_skip() throws Exception {
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "【TESTJOB】").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId));

        verify(candidateProfileFeignClient, never()).getUserProfile(anyLong());
    }

    /**
     * 只选行业 → 有筛选，不进画像模式，Feign 不调用 [R8]
     */
    @Test
    void industryFilter_skipProfile() throws Exception {
        insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("industryGroupCode", "IT").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(candidateProfileFeignClient, never()).getUserProfile(anyLong());
    }

    // ==================== 薪资（元→分，[R2/R5]） ====================

    /**
     * 双边薪资：岗位区间完全包含画像区间=25，相交=18
     */
    @Test
    void salaryBothSides() throws Exception {
        // 画像 [1500,2500]元 = [150000,250000]分；A[1000,3000]元 包含 → 25，B[2000,3500]元 相交 → 18
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileSalary(1500, 2500)));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");
        Long bId = insertJob(2, "BACHELOR", 200000L, 350000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))   // 25 > 18，A 在前
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(35))   // 25 + freshness 10
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + bId + ")].recommendScore").value(28)); // 18+10
    }

    /**
     * 单边薪资：仅 min=岗位max≥min→18；仅 max=岗位min≤max→18 [R2]
     */
    @Test
    void salaryOnlyMinAndMax() throws Exception {
        // 仅 min=1500元：A[max=3000元≥1500→18]，B[max=900元<1500→0]
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileSalary(1500, null)));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");
        Long bId = insertJob(2, "BACHELOR", 50000L, 90000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))   // 28 > 10，A 在前
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(28))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + bId + ")].recommendScore").value(10));

        // 仅 max=2500元：A[min=1000元≤2500→18]，B[min=3000元>2500→0]
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileSalary(null, 2500)));
        Long cId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");
        Long dId = insertJob(2, "BACHELOR", 300000L, 400000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + cId + ")].recommendScore").value(28))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + dId + ")].recommendScore").value(10));
    }

    /**
     * 脏数据薪资（min>max）→ 整组置 null，全画像非法 → 降级最新排序 [R5]
     * <p>阶段6.3：全非法画像无有效信号，走轻量查询，recommendScore 恒 0。</p>
     */
    @Test
    void salaryDirty() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileSalary(3000, 1000))); // min>max
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(0));
    }

    // ==================== 城市（FIND_IN_SET 精确匹配，[R3/R10]） ====================

    /**
     * 城市精确匹配：带空格逗号串规范化后 FIND_IN_SET 命中，不误命中子串
     */
    @Test
    void cityExactMatch() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileCity("北京, 上海"))); // 含空格
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");
        Long bId = insertJob(2, "BACHELOR", 100000L, 300000L, "广州");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))   // 城市命中 15 → 25 总分，B 仅 freshness
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(25))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + bId + ")].recommendScore").value(10));
    }

    /**
     * 中文逗号分隔：北京，上海 → 规范化后命中北京岗位 [R10]
     */
    @Test
    void cityChineseComma() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileCity("北京，上海"))); // 中文逗号
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(25)); // 15 + freshness 10
    }

    // ==================== 期望岗位（LIKE 转义，[R4]） ====================

    /**
     * desiredJob 含 LIKE 通配符 → 转义后按字面匹配，无 SQL 异常 [R4]
     */
    @Test
    void desiredJobSpecialChars() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileJob("Java%"))); // 转义后按字面 % 匹配
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");
        jdbcTemplate.update("UPDATE job_post SET title = '【TESTJOB】Java%专项' WHERE id = ?", aId);

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(35)); // 25+10
    }

    // ==================== 年限/学历映射 ====================

    /**
     * PHD→6、FRESH→2：学历满足(10) + 年限上限≥岗位最低(15) 得分
     */
    @Test
    void educationAndWorkYearsMapping() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileEduWorkYears("PHD", "FRESH")));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京"); // minExp=2、学历BACHELOR

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))
                // 年限 15 + 学历 10 + freshness 10 = 35
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(35));
    }

    // ==================== 边界 ====================

    /**
     * 部分画像（仅 desiredJob）→ 正常返回，其余维度计 0 [R11 相邻]
     */
    @Test
    void partialProfile() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileJob("Java")));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].jobId").value(aId))
                .andExpect(jsonPath("$.data.list[0].recommendScore").value(35)); // 25 + freshness 10
    }

    /**
     * 画像完全不匹配 → 岗位仍返回（不过滤），仅按新鲜度排序 [R6]
     */
    @Test
    void noFilterStillReturns() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(profileCity("纽约"))); // 不存在城市
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 各维度 0，仅 freshness 10，但岗位仍在列表中（证明不过滤）
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(10));
    }

    /**
     * 画像存在但六字段全空 → 无有效信号，降级最新排序 [R11 收紧]
     * <p>阶段6.3：全空画像不启用画像模式，走轻量查询，recommendScore 恒 0。</p>
     */
    @Test
    void emptyProfile_fallsBackToLatest() throws Exception {
        when(candidateProfileFeignClient.getUserProfile(anyLong()))
                .thenReturn(Result.success(new CandidateProfileDTO()));
        Long aId = insertJob(2, "BACHELOR", 100000L, 300000L, "北京");

        mockMvc.perform(get("/api/v1/jobs").param("size", "50").headers(candidateHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[?(@.jobId == " + aId + ")].recommendScore").value(0));
    }

    // ==================== 辅助方法 ====================

    /**
     * CANDIDATE 请求头（模拟网关注入）
     */
    private HttpHeaders candidateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.set("X-User-Role", "CANDIDATE");
        return headers;
    }

    /**
     * HR 请求头（非候选人，验证不进画像模式）
     */
    private HttpHeaders hrHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "2001");
        headers.set("X-User-Role", "HR");
        return headers;
    }

    /**
     * 六维完整画像
     */
    private CandidateProfileDTO fullProfile() {
        CandidateProfileDTO p = new CandidateProfileDTO();
        p.setDesiredJob("Java");
        p.setDesiredCity("北京");
        p.setDesiredSalaryMin(1500);
        p.setDesiredSalaryMax(2500);
        p.setWorkYears("3-5");
        p.setEducation("BACHELOR");
        return p;
    }

    /**
     * 仅薪资画像（元）
     */
    private CandidateProfileDTO profileSalary(Integer minYuan, Integer maxYuan) {
        CandidateProfileDTO p = new CandidateProfileDTO();
        p.setDesiredSalaryMin(minYuan);
        p.setDesiredSalaryMax(maxYuan);
        return p;
    }

    /**
     * 仅城市画像
     */
    private CandidateProfileDTO profileCity(String city) {
        CandidateProfileDTO p = new CandidateProfileDTO();
        p.setDesiredCity(city);
        return p;
    }

    /**
     * 仅期望岗位画像
     */
    private CandidateProfileDTO profileJob(String job) {
        CandidateProfileDTO p = new CandidateProfileDTO();
        p.setDesiredJob(job);
        return p;
    }

    /**
     * 仅学历+年限画像
     */
    private CandidateProfileDTO profileEduWorkYears(String education, String workYears) {
        CandidateProfileDTO p = new CandidateProfileDTO();
        p.setEducation(education);
        p.setWorkYears(workYears);
        return p;
    }

    /**
     * 插入已发布测试岗位（默认：IT 行业、城市北京、面议否、当天发布、无画像）
     */
    private Long insertJob(int minExp, String education, Long salaryMin, Long salaryMax, String cityName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_post (company_id, title, industry_group_code, industry_code, city_code, "
                            + "city_name, min_experience_years, education_requirement, salary_min_amount, "
                            + "salary_max_amount, salary_currency, salary_period, salary_months, "
                            + "is_salary_negotiable, total_hc, jd_text, status, published_at, deleted_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, NULL, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, TEST_COMPANY_ID);
            ps.setString(2, "【TESTJOB】Java后端工程师");
            ps.setString(3, "IT");
            ps.setString(4, "IT");
            ps.setString(5, "110000");
            ps.setString(6, cityName);
            ps.setInt(7, minExp);
            ps.setString(8, education);
            ps.setLong(9, salaryMin);
            ps.setLong(10, salaryMax);
            ps.setString(11, "CNY");
            ps.setString(12, "MONTH");
            ps.setInt(13, 12);
            ps.setInt(14, 0);
            ps.setInt(15, 2);
            ps.setString(16, "负责后端服务开发与维护");
            ps.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(18, 1);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}

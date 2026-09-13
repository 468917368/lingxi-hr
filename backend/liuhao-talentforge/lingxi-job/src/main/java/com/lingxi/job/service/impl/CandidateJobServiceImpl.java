package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.domain.dto.response.FavoriteResponse;
import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import com.lingxi.job.feign.dto.CandidateProfileDTO;
import com.lingxi.job.domain.entity.JobFavorite;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.vo.FavoriteJobVO;
import com.lingxi.job.domain.vo.JobCardVO;
import com.lingxi.job.domain.vo.JobDetailVO;
import com.lingxi.job.enums.EducationRequirementEnum;
import com.lingxi.job.enums.IndustryCodeEnum;
import com.lingxi.job.enums.SortByEnum;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.feign.CandidateProfileFeignClient;
import com.lingxi.job.feign.UserFeignClient;
import com.lingxi.job.feign.dto.UserCompanyDTO;
import com.lingxi.job.feign.dto.UserInfoDTO;
import com.lingxi.job.mapper.JobFavoriteMapper;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.service.CandidateJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C端岗位发现服务实现
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateJobServiceImpl implements CandidateJobService {

    /** 技能标签返回上限，避免一次返回过大数据 */
    private static final int SKILL_LIMIT = 100;

    /** 企业归属无法确认时的通用展示文案（不暴露错误企业名） */
    private static final String DEFAULT_COMPANY_NAME = "招聘企业";

    /** 创建人缺失/用户服务不可用时的通用展示文案（不暴露用户ID） */
    private static final String DEFAULT_CREATOR_NAME = "招聘负责人";

    private final JobPostMapper jobPostMapper;
    private final JobProfileMapper jobProfileMapper;
    private final JobFavoriteMapper jobFavoriteMapper;
    private final CandidateProfileFeignClient candidateProfileFeignClient;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(readOnly = true)
    public PageResult<JobCardVO> searchJobs(Map<String, Object> query) {
        // 1. 规范化：String 参数 trim + 空转 null；skillTags 逐项 trim + 去重（LinkedHashSet 保序）
        String keyword = trimToNull(query.get("keyword"));
        String industryGroupCode = trimToNull(query.get("industryGroupCode"));
        String industryCode = trimToNull(query.get("industryCode"));
        String cityCode = trimToNull(query.get("cityCode"));
        String education = trimToNull(query.get("education"));
        String jobType = trimToNull(query.get("jobType"));
        String sortBy = trimToNull(query.get("sortBy"));
        if (!StringUtils.hasText(sortBy)) {
            sortBy = SortByEnum.RECOMMENDED.getCode();
        }

        List<String> skillTags = normalizeSkillTags(query.get("skillTags"));
        Long salaryMin = query.get("salaryMin") == null ? null : Long.valueOf(String.valueOf(query.get("salaryMin")));
        Long salaryMax = query.get("salaryMax") == null ? null : Long.valueOf(String.valueOf(query.get("salaryMax")));
        Integer experienceMax = query.get("experienceMax") == null ? null : Integer.valueOf(String.valueOf(query.get("experienceMax")));
        int page = query.get("page") == null ? 1 : Integer.parseInt(String.valueOf(query.get("page")));
        int size = query.get("size") == null ? 20 : Integer.parseInt(String.valueOf(query.get("size")));

        // 2. 校验（对规范化后参数）
        if (page < 1 || page > 100) {
            throw new BusinessException(400, "页码需在1~100之间");
        }
        if (size < 1 || size > 50) {
            throw new BusinessException(400, "每页条数需在1~50之间");
        }
        if (StringUtils.hasText(industryGroupCode) && !IndustryCodeEnum.isValid(industryGroupCode)) {
            throw new BusinessException(400, "一级行业编码不合法");
        }
        if (StringUtils.hasText(industryCode) && !IndustryCodeEnum.isValid(industryCode)) {
            throw new BusinessException(400, "行业编码不合法");
        }
        // 行业层级：两级同时传入时必须一致（第一版两级相同）
        if (StringUtils.hasText(industryGroupCode) && StringUtils.hasText(industryCode)
                && !industryGroupCode.equals(industryCode)) {
            throw new BusinessException(400, "一级行业与具体行业编码不一致");
        }
        if (StringUtils.hasText(education) && !EducationRequirementEnum.isValid(education)) {
            throw new BusinessException(400, "学历编码不合法");
        }
        if (!SortByEnum.isValid(sortBy)) {
            throw new BusinessException(400, "排序方式不合法");
        }
        if (salaryMin != null && salaryMin < 0) {
            throw new BusinessException(400, "期望最低薪资不能为负");
        }
        if (salaryMax != null && salaryMax < 0) {
            throw new BusinessException(400, "期望最高薪资不能为负");
        }
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            throw new BusinessException(400, "期望最低薪资不能高于最高薪资");
        }
        if (experienceMax != null && experienceMax < 0) {
            throw new BusinessException(400, "可接受最高经验不能为负");
        }

        // 3. 构造查询参数
        Map<String, Object> q = new HashMap<>();
        q.put("keyword", escapeKeyword(keyword));
        q.put("industryGroupCode", industryGroupCode);
        q.put("industryCode", industryCode);
        q.put("cityCode", cityCode);
        q.put("jobType", jobType);
        q.put("sortBy", sortBy);
        q.put("salaryMin", salaryMin);
        q.put("salaryMax", salaryMax);
        q.put("experienceMax", experienceMax);
        q.put("skillTags", skillTags);
        // 学历：生成可满足编码集合 + 用户学历等级（供推荐分接近度）
        if (StringUtils.hasText(education)) {
            EducationRequirementEnum userEdu = EducationRequirementEnum.fromCode(education);
            List<String> educationCodes = new ArrayList<>();
            for (EducationRequirementEnum e : EducationRequirementEnum.values()) {
                if (e.getLevel() <= userEdu.getLevel()) {
                    educationCodes.add(e.getCode());
                }
            }
            q.put("educationCodes", educationCodes);
            q.put("userEducationLevel", userEdu.getLevel());
        }

        // 3.5 画像推荐模式（阶段6.3 精确分流）：仅 CANDIDATE + 无筛选 + RECOMMENDED 且画像有有效信号才启用；
        //     全空/失败/缺失画像不写 profileMode，走最新排序降级（计划验收标准2）
        boolean noUserFilters = isEmptySearch(keyword, industryGroupCode, industryCode, cityCode,
                skillTags, salaryMin, salaryMax, experienceMax, education, jobType);
        boolean profileEnabled = false;
        if (SortByEnum.RECOMMENDED.getCode().equals(sortBy) && noUserFilters && isCurrentCandidate()) {
            profileEnabled = fillProfileParams(q);
        }
        // LATEST 或 无筛选 RECOMMENDED 降级 → 轻量查询（不计算推荐分，recommendScore=0）；
        // 有筛选 或 有效画像 → 原推荐 SQL（完整推荐分/薪资排序）
        boolean useLatestQuery = SortByEnum.LATEST.getCode().equals(sortBy)
                || (SortByEnum.RECOMMENDED.getCode().equals(sortBy) && noUserFilters && !profileEnabled);

        // 4. 分页查询（推荐分已在 SQL 内计算，数据库内完成全局排序后再分页）
        PageHelper.startPage(page, size);
        List<JobCardVO> list = useLatestQuery
                ? jobPostMapper.selectLatestForCandidateSearch(q)
                : jobPostMapper.selectForCandidateSearch(q);
        PageInfo<JobCardVO> pageInfo = new PageInfo<>(list);

        // 5. 组装：扁平薪资→嵌套 SalaryVO、skillTagsRaw→skillTags、industryName 枚举转换，清空承接字段
        for (JobCardVO vo : list) {
            fillSalary(vo);
            vo.setSkillTags(parseSkillTags(vo.getSkillTagsRaw()));
            vo.setSkillTagsRaw(null);
            IndustryCodeEnum industry = IndustryCodeEnum.fromCode(vo.getIndustryGroupCode());
            vo.setIndustryName(industry == null ? null : industry.getDesc());
        }
        // 6. 企业名与招聘负责人：本页去重收集创建人ID，一次批量查询（每页仅一次 batch）；
        //    企业名仅在用户当前企业与岗位 company_id 匹配时回填，否则降级为通用文案（不阻断主流程）
        List<Long> creatorIds = list.stream()
                .map(JobCardVO::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserInfoDTO> userMap = fetchUsers(creatorIds);
        for (JobCardVO vo : list) {
            CreatorDisplay display = resolveCreatorDisplay(vo.getCompanyId(), userMap.get(vo.getCreatedBy()));
            vo.setCompanyName(display.companyName);
            vo.setCreatorName(display.creatorName);
        }
        return PageResult.of(list, pageInfo.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public JobDetailVO getJobDetail(Long jobId) {
        // 仅 PUBLISHED 且未删除，否则 2101
        JobPost post = jobPostMapper.selectPublishedById(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }

        // 企业隔离查询画像（命中 uk_job_profile 联合唯一键）
        JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, post.getCompanyId());

        JobDetailVO vo = new JobDetailVO();
        vo.setJobId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setIndustryGroupCode(post.getIndustryGroupCode());
        vo.setIndustryCode(post.getIndustryCode());
        IndustryCodeEnum industry = IndustryCodeEnum.fromCode(post.getIndustryGroupCode());
        vo.setIndustryName(industry == null ? null : industry.getDesc());
        vo.setCityCode(post.getCityCode());
        vo.setCityName(post.getCityName());
        vo.setMinExperienceYears(post.getMinExperienceYears());
        vo.setEducationRequirement(post.getEducationRequirement());
        vo.setSalary(buildSalaryVO(post));
        vo.setJdText(post.getJdText());
        vo.setJdSummary(post.getJdSummary());
        vo.setPublishedAt(post.getPublishedAt());

        // 画像缺失：profile 对象始终存在，各字段默认值
        JobDetailVO.Profile profileVO = new JobDetailVO.Profile();
        if (profile != null) {
            profileVO.setJobType(profile.getJobType());
            profileVO.setCoreSkills(parseCoreSkills(profile.getCoreSkills()));
            profileVO.setSoftSkills(parseSoftSkills(profile.getSoftSkills()));
            profileVO.setIndustryExperience(profile.getIndustryExperience());
            profileVO.setInterviewFocus(parseInterviewFocus(profile.getInterviewFocus()));
            profileVO.setProfileConfirmed(profile.getConfirmedAt() != null);
        } else {
            profileVO.setJobType(null);
            profileVO.setCoreSkills(Collections.emptyList());
            profileVO.setSoftSkills(Collections.emptyList());
            profileVO.setIndustryExperience(null);
            profileVO.setInterviewFocus(Collections.emptyList());
            profileVO.setProfileConfirmed(false);
        }
        vo.setProfile(profileVO);
        // 企业名与招聘负责人：详情复用列表同款批量查询（单元素），不维护第二套归属校验/降级逻辑
        Map<Long, UserInfoDTO> userMap = fetchUsers(
                post.getCreatedBy() == null ? Collections.emptyList()
                        : Collections.singletonList(post.getCreatedBy()));
        CreatorDisplay display = resolveCreatorDisplay(post.getCompanyId(), userMap.get(post.getCreatedBy()));
        vo.setCompanyName(display.companyName);
        vo.setCreatorName(display.creatorName);
        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 批量查用户信息（只调一次 batch，不逐 id 单查——用户服务异常时不放大请求）：
     * 返回 Map&lt;userId, DTO&gt;；失败/缺失/返回空 → 空 Map，调用方按通用文案兜底。
     */
    private Map<Long, UserInfoDTO> fetchUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<List<UserInfoDTO>> result = userFeignClient.batchUsers(
                    userIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData().stream()
                        .filter(u -> u != null && u.getId() != null)
                        .collect(Collectors.toMap(UserInfoDTO::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("批量查询用户失败，C 端企业/负责人降级为通用文案: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * C 端企业名与招聘负责人统一解析（列表/详情共用，保证语义一致）：
     * 企业名仅在"用户当前企业 id 与岗位 company_id 匹配且企业名非空"时回填真实值，
     * 否则降级为通用文案（宁可不展示也不展示错误企业名）；负责人姓名取真实姓名，缺失降级。
     */
    private CreatorDisplay resolveCreatorDisplay(Long jobCompanyId, UserInfoDTO user) {
        String creatorName = user != null && StringUtils.hasText(user.getName())
                ? user.getName() : DEFAULT_CREATOR_NAME;
        UserCompanyDTO company = user == null ? null : user.getCompany();
        boolean companyMatched = company != null
                && Objects.equals(company.getId(), jobCompanyId)
                && StringUtils.hasText(company.getName());
        String companyName = companyMatched ? company.getName() : DEFAULT_COMPANY_NAME;
        return new CreatorDisplay(companyName, creatorName);
    }

    /**
     * 企业名 + 负责人姓名展示值（避免散落两个返回值）
     */
    private static final class CreatorDisplay {
        private final String companyName;
        private final String creatorName;

        private CreatorDisplay(String companyName, String creatorName) {
            this.companyName = companyName;
            this.creatorName = creatorName;
        }
    }

    /**
     * String 参数 trim，空串转 null
     */
    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * skillTags 规范化：逐项 trim，空标签抛 400；去重保持输入顺序
     */
    private List<String> normalizeSkillTags(Object value) {
        if (value == null) {
            return null;
        }
        List<?> raw = value instanceof List ? (List<?>) value : Collections.singletonList(value);
        Set<String> set = new LinkedHashSet<>();
        for (Object item : raw) {
            String tag = item == null ? "" : String.valueOf(item).trim();
            if (tag.isEmpty()) {
                throw new BusinessException(400, "技能标签不能为空");
            }
            set.add(tag);
        }
        if (set.size() > 10) {
            throw new BusinessException(400, "技能标签最多10个");
        }
        return new ArrayList<>(set);
    }

    /**
     * keyword LIKE 通配符转义：= → ==、% → =%、_ → =_（SQL 侧配套 ESCAPE '='）
     */
    private String escapeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("=", "==").replace("%", "=%").replace("_", "=_");
    }

    /**
     * 当前用户是否为 CANDIDATE（仅读 UserContext；LATEST 路径不得调用 Feign，故仅在此判断角色）
     */
    private boolean isCurrentCandidate() {
        UserDTO user = UserContext.get();
        return user != null && user.isCandidate();
    }

    /**
     * 空筛选判断：10 个筛选参数全为 null 才算"无筛选"，才允许进入画像推荐模式 [R8]
     */
    private boolean isEmptySearch(String keyword, String industryGroupCode, String industryCode,
            String cityCode, List<String> skillTags, Long salaryMin, Long salaryMax,
            Integer experienceMax, String education, String jobType) {
        return keyword == null && industryGroupCode == null && industryCode == null && cityCode == null
                && skillTags == null && salaryMin == null && salaryMax == null
                && experienceMax == null && education == null && jobType == null;
    }

    /**
     * 画像 → 推荐参数（1113 显式降级 [R1]；中英文逗号统一 [R10]；阶段6.3 收紧：全空/全非法不启用画像模式）
     *
     * @return true=已写 q 的 profileMode 及可评分信号；false=降级最新排序（不写画像参数，不抛错）
     */
    private boolean fillProfileParams(Map<String, Object> q) {
        Long userId = UserContext.getUserId();
        Result<CandidateProfileDTO> result = candidateProfileFeignClient.getUserProfile(userId);
        // R1：fallback 只覆盖异常；画像缺失以 HTTP 200 + code=1113 + data=null 返回，必须显式判断
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.warn("获取用户画像失败，降级为最新排序: userId={}, code={}", userId,
                    result == null ? "null" : result.getCode());
            return false;
        }
        CandidateProfileDTO p = result.getData();
        // 期望岗位：复用 escapeKeyword 转义 % _ = [R4]
        String desiredJob = escapeKeyword(trimToNull(p.getDesiredJob()));
        // 期望城市：中英文逗号统一 → 无空格逗号串 [R3/R10]
        String desiredCities = normalizeCities(p.getDesiredCity());
        // 期望薪资：元→分 ×100L（防 INT×100 溢出）；负值或 min>max 整组置 null 不计 [R2/R5]
        Long salaryMin = p.getDesiredSalaryMin() == null ? null : p.getDesiredSalaryMin() * 100L;
        Long salaryMax = p.getDesiredSalaryMax() == null ? null : p.getDesiredSalaryMax() * 100L;
        if (salaryMin != null && salaryMin < 0) {
            salaryMin = null;
        }
        if (salaryMax != null && salaryMax < 0) {
            salaryMax = null;
        }
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            salaryMin = null;
            salaryMax = null;
        }
        // 年限/学历：未知枚举 → null，该项不计
        Integer workYearsMax = mapWorkYears(p.getWorkYears());
        Integer educationLevel = mapEducation(p.getEducation());
        // 仅至少一个有效信号才启用画像模式；全空/全非法 → 降级最新排序（不写 profileMode，[R11 收紧]）
        if (!hasEffectiveProfileSignal(desiredJob, desiredCities, salaryMin, salaryMax,
                workYearsMax, educationLevel)) {
            log.debug("画像全空或全非法，降级最新排序: userId={}", userId);
            return false;
        }
        q.put("profileMode", true);
        q.put("desiredJob", desiredJob);
        q.put("desiredCities", desiredCities);
        q.put("desiredSalaryMin", salaryMin);
        q.put("desiredSalaryMax", salaryMax);
        q.put("userWorkYearsMax", workYearsMax);
        q.put("userEducationLevel", educationLevel);
        return true;
    }

    /**
     * 画像有效信号：期望岗位/期望城市/有效薪资/工作年限/学历 任一存在即为有效
     */
    private boolean hasEffectiveProfileSignal(String desiredJob, String desiredCities,
                                              Long desiredSalaryMin, Long desiredSalaryMax,
                                              Integer workYearsMax, Integer educationLevel) {
        return StringUtils.hasText(desiredJob)
                || StringUtils.hasText(desiredCities)
                || desiredSalaryMin != null
                || desiredSalaryMax != null
                || workYearsMax != null
                || educationLevel != null;
    }

    /**
     * 城市规范化：中英文逗号统一 → split/trim/去空/去重 → 无空格逗号串 [R3/R10]
     */
    private String normalizeCities(String desiredCity) {
        if (!StringUtils.hasText(desiredCity)) {
            return null;
        }
        return Arrays.stream(desiredCity.replace("，", ",").split(","))
                .map(String::trim)
                .filter(s -> StringUtils.hasText(s))
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 工作年限 → 可接受年限上限（FRESH→2、10+→10 封顶；未知枚举→null 该项不计）
     */
    private Integer mapWorkYears(String workYears) {
        if (workYears == null) {
            return null;
        }
        switch (workYears) {
            case "FRESH":
                return 2;
            case "1-3":
                return 3;
            case "3-5":
                return 5;
            case "5-10":
                return 10;
            case "10+":
                return 10;
            default:
                return null;
        }
    }

    /**
     * 学历 → 等级（COLLEGE→大专3、BACHELOR→4、MASTER→5、PHD→博士6；未知→null 该项不计）
     */
    private Integer mapEducation(String education) {
        if (education == null) {
            return null;
        }
        switch (education) {
            case "COLLEGE":
                return EducationRequirementEnum.ASSOCIATE.getLevel();
            case "BACHELOR":
                return EducationRequirementEnum.BACHELOR.getLevel();
            case "MASTER":
                return EducationRequirementEnum.MASTER.getLevel();
            case "PHD":
                return EducationRequirementEnum.DOCTOR.getLevel();
            default:
                return null;
        }
    }

    /**
     * 列表项：扁平薪资字段组装成嵌套 SalaryVO 后清空扁平字段
     */
    private void fillSalary(JobCardVO vo) {
        JobCardVO.SalaryVO salary = new JobCardVO.SalaryVO();
        salary.setMinAmount(vo.getSalaryMinAmount());
        salary.setMaxAmount(vo.getSalaryMaxAmount());
        salary.setCurrency(vo.getSalaryCurrency());
        salary.setPeriod(vo.getSalaryPeriod());
        salary.setMonths(vo.getSalaryMonths());
        salary.setNegotiable(vo.getSalaryNegotiable() != null && vo.getSalaryNegotiable() == 1);
        salary.setRawText(vo.getSalaryRawText());
        vo.setSalary(salary);
        vo.setSalaryMinAmount(null);
        vo.setSalaryMaxAmount(null);
        vo.setSalaryCurrency(null);
        vo.setSalaryPeriod(null);
        vo.setSalaryMonths(null);
        vo.setSalaryNegotiable(null);
        vo.setSalaryRawText(null);
    }

    /**
     * 解析 core_skills JSON → 技能名称列表
     */
    private List<String> parseSkillTags(String coreSkillsJson) {
        if (!StringUtils.hasText(coreSkillsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> skills = JsonUtil.getMapper().readValue(coreSkillsJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            return skills.stream()
                    .map(s -> s.get("name") == null ? null : String.valueOf(s.get("name")))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析 core_skills 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 详情：岗位薪资字段组装成嵌套 SalaryVO
     */
    private JobDetailVO.SalaryVO buildSalaryVO(JobPost post) {
        JobDetailVO.SalaryVO salary = new JobDetailVO.SalaryVO();
        salary.setMinAmount(post.getSalaryMinAmount());
        salary.setMaxAmount(post.getSalaryMaxAmount());
        salary.setCurrency(post.getSalaryCurrency());
        salary.setPeriod(post.getSalaryPeriod());
        salary.setMonths(post.getSalaryMonths());
        salary.setNegotiable(post.getIsSalaryNegotiable() != null && post.getIsSalaryNegotiable() == 1);
        salary.setRawText(post.getSalaryRawText());
        return salary;
    }

    /**
     * 解析 core_skills JSON → 结构化核心技能列表
     */
    private List<JobDetailVO.CoreSkill> parseCoreSkills(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<JobRequirementResponse.CoreSkill> skills = JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<JobRequirementResponse.CoreSkill>>() {});
            // 脱敏：仅保留 name/level/required，丢弃 basis/confidence
            List<JobDetailVO.CoreSkill> result = new ArrayList<>();
            for (JobRequirementResponse.CoreSkill s : skills) {
                JobDetailVO.CoreSkill cs = new JobDetailVO.CoreSkill();
                cs.setName(s.getName());
                cs.setLevel(s.getLevel());
                cs.setRequired(s.getRequired());
                result.add(cs);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 core_skills 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 soft_skills JSON → 结构化软能力列表
     */
    private List<JobDetailVO.SoftSkill> parseSoftSkills(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<JobRequirementResponse.SoftSkill> skills = JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<JobRequirementResponse.SoftSkill>>() {});
            // 脱敏：仅保留 name/importance，丢弃 inferred/confidence
            List<JobDetailVO.SoftSkill> result = new ArrayList<>();
            for (JobRequirementResponse.SoftSkill s : skills) {
                JobDetailVO.SoftSkill ss = new JobDetailVO.SoftSkill();
                ss.setName(s.getName());
                ss.setImportance(s.getImportance());
                result.add(ss);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 soft_skills 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 interview_focus JSON → 考察重点列表
     */
    private List<String> parseInterviewFocus(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtil.getMapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 interview_focus 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobOptionsResponse.CityOption> listCityOptions() {
        // 从已发布岗位去重读取城市，保证搜索选项与真实数据一致
        return jobPostMapper.selectCityOptions();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listSkillOptions() {
        // 从已发布岗位画像 coreSkills 提取技能名（SQL 层去空去重排序），LIMIT 控制返回数量
        return jobPostMapper.selectSkillOptions(SKILL_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteResponse favoriteJob(Long jobId, Boolean favorited) {
        Long candidateId = requireCandidateId();
        boolean target = Boolean.TRUE.equals(favorited);
        if (target) {
            // 收藏：仅 PUBLISHED 且未删除岗位，否则 2101（与 C 端详情 selectPublishedById 语义一致：不可见=不存在）
            JobPost post = jobPostMapper.selectPublishedById(jobId);
            if (post == null) {
                throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
            }
            // 幂等：已收藏直接成功（不抛 2106，JOB_ALREADY_FAVORITED 保留定义不再抛出）
            if (jobFavoriteMapper.selectByCandidateAndJob(candidateId, jobId) != null) {
                return new FavoriteResponse(jobId, true);
            }
            JobFavorite favorite = new JobFavorite();
            favorite.setCandidateId(candidateId);
            favorite.setJobId(jobId);
            favorite.setCreatedAt(LocalDateTime.now());
            try {
                jobFavoriteMapper.insert(favorite);
            } catch (DuplicateKeyException e) {
                // 并发重复收藏撞 uk_candidate_job 唯一索引 → 幂等成功
                log.info("并发重复收藏幂等成功: candidateId={}, jobId={}", candidateId, jobId);
            }
            return new FavoriteResponse(jobId, true);
        }
        // 取消收藏：物理删除，不校验岗位状态（岗位已关闭/删除也可取消）；未收藏删除 0 行 → 幂等成功
        jobFavoriteMapper.deleteByCandidateAndJob(candidateId, jobId);
        return new FavoriteResponse(jobId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FavoriteJobVO> listFavorites(int page, int size) {
        Long candidateId = requireCandidateId();
        validatePage(page, size);
        PageHelper.startPage(page, size);
        List<FavoriteJobVO> list = jobFavoriteMapper.selectFavoriteList(candidateId);
        PageInfo<FavoriteJobVO> pageInfo = new PageInfo<>(list);
        // industryName：job_post 无该列，Service 用枚举组装（复用 JobCardVO 组装逻辑）
        for (FavoriteJobVO vo : list) {
            IndustryCodeEnum industry = IndustryCodeEnum.fromCode(vo.getIndustryGroupCode());
            vo.setIndustryName(industry == null ? null : industry.getDesc());
        }
        return PageResult.of(list, pageInfo.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listFavoriteJobIds() {
        // candidateId 从 UserContext 取，禁止外部传 ID 防误传
        Long candidateId = requireCandidateId();
        return jobFavoriteMapper.selectJobIdsByCandidateId(candidateId);
    }

    // ==================== 收藏私有辅助 ====================

    /** 候选人上下文（userId 从 UserContext 取，@RequireRole(CANDIDATE) 已兜底登录，此处防御性校验） */
    private Long requireCandidateId() {
        Long candidateId = UserContext.getUserId();
        if (candidateId == null) {
            throw new BusinessException(403, "缺少用户上下文");
        }
        return candidateId;
    }

    /** 收藏分页边界校验：page 1~100、size 1~50（沿用 searchJobs 惯例，Service 层兜底） */
    private void validatePage(int page, int size) {
        if (page < 1 || page > 100) {
            throw new BusinessException(400, "页码需在1~100之间");
        }
        if (size < 1 || size > 50) {
            throw new BusinessException(400, "每页条数需在1~50之间");
        }
    }
}

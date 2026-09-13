package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.enums.JobStatus;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.domain.dto.request.JobCreateRequest;
import com.lingxi.job.domain.dto.request.JobStatusActionRequest;
import com.lingxi.job.domain.dto.request.JobUpdateRequest;
import com.lingxi.job.domain.dto.response.JobCreateResponse;
import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import com.lingxi.job.domain.dto.response.JobStatusResponse;
import com.lingxi.job.domain.dto.response.JobUpdateResponse;
import com.lingxi.job.domain.entity.JobCityDict;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.entity.JobStatusLog;
import com.lingxi.job.enums.CloseReasonEnum;
import com.lingxi.job.enums.EducationRequirementEnum;
import com.lingxi.job.enums.IndustryCodeEnum;
import com.lingxi.job.enums.ProfileSourceEnum;
import com.lingxi.job.enums.SalaryPeriodEnum;
import com.lingxi.job.domain.vo.HrJobDetailVO;
import com.lingxi.job.domain.vo.HrJobListVO;
import com.lingxi.job.domain.vo.JobStatusLogVO;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.feign.UserFeignClient;
import com.lingxi.job.feign.dto.UserInfoDTO;
import com.lingxi.job.mapper.JobCityDictMapper;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.mapper.JobStatusLogMapper;
import com.lingxi.job.service.HrJobService;
import com.lingxi.job.validator.JobProfileInputSanitizer;
import com.lingxi.job.validator.JobStateValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 岗位管理服务实现（B端 HR 接口）
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrJobServiceImpl implements HrJobService {

    private final JobPostMapper jobPostMapper;
    private final JobProfileMapper jobProfileMapper;
    private final JobStatusLogMapper jobStatusLogMapper;
    private final JobCityDictMapper jobCityDictMapper;
    private final UserFeignClient userFeignClient;
    private final JobProfileInputSanitizer jobProfileInputSanitizer;

    /** JD摘要最大长度 */
    private static final int JD_SUMMARY_MAX_LENGTH = 1000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCreateResponse createJob(JobCreateRequest request) {
        Long companyId = requireCompanyId();
        Long userId = UserContext.getUserId();

        // 1. 薪资校验（negotiable=false 时 min/max/months 必填且合法）
        validateSalary(request.getSalary());

        // 2. 行业/学历校验
        validateIndustry(request.getIndustryGroupCode(), request.getIndustryCode());
        String educationRequirement = resolveEducation(request.getEducationRequirement());

        // 4. 画像来源校验
        JobCreateRequest.JobProfileDTO profileDTO = request.getProfile();
        if (!ProfileSourceEnum.isValid(profileDTO.getProfileSource())) {
            throw new BusinessException(400, "画像来源编码不合法");
        }

        // 4.5 技能标签清洗校验（任何 DB 写操作之前；命中广告/空/超长直接 400，不做脱敏替换保存）
        profileDTO.setCoreSkills(jobProfileInputSanitizer.sanitize(profileDTO.getCoreSkills()));
        profileDTO.setSoftSkills(jobProfileInputSanitizer.sanitizeSoftSkills(profileDTO.getSoftSkills()));

        // 5. 组装并插入 job_post
        JobPost post = buildJobPost(request, companyId, userId, educationRequirement);
        jobPostMapper.insert(post);

        // 6. 组装并插入 job_profile（version 从 1 开始，与 DTO profileVersion 对应）
        JobProfile profile = buildJobProfile(request, profileDTO, companyId, post.getId(), userId);
        jobProfileMapper.insert(profile);

        // 7. 返回创建结果
        JobCreateResponse resp = new JobCreateResponse();
        resp.setJobId(post.getId());
        resp.setStatus(post.getStatus());
        resp.setVersion(post.getVersion());
        resp.setProfileVersion(profile.getVersion());
        resp.setCreatedAt(post.getCreatedAt());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobUpdateResponse updateJob(Long jobId, JobUpdateRequest request) {
        Long companyId = requireCompanyId();
        Long userId = UserContext.getUserId();

        // 1. 企业隔离查询：岗位不存在或不属于本企业 → 2101
        JobPost post = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }

        // 2. 仅 DRAFT 可编辑
        if (!JobStatus.DRAFT.getCode().equals(post.getStatus())) {
            throw new BusinessException(JobErrorCode.JOB_NOT_EDITABLE);
        }

        // 3. 薪资/行业/学历校验
        validateSalary(request.getSalary());
        validateIndustry(request.getIndustryGroupCode(), request.getIndustryCode());
        String educationRequirement = resolveEducation(request.getEducationRequirement());

        // 4. 画像三件套配对校验（先于写操作，避免无谓回滚）：全缺=不改画像；全出现=更新画像；部分出现=400
        JobCreateRequest.JobProfileDTO profileDTO = request.getProfile();
        int presentCount = 0;
        presentCount += profileDTO != null ? 1 : 0;
        presentCount += request.getProfileVersion() != null ? 1 : 0;
        presentCount += request.getProfileConfirmed() != null ? 1 : 0;
        if (presentCount != 0 && presentCount != 3) {
            throw new BusinessException(400, "画像信息必须整体提供或整体不提供");
        }
        boolean updateProfile = presentCount == 3;
        // 更新画像时画像来源必须合法（与创建校验一致）
        if (updateProfile && !ProfileSourceEnum.isValid(profileDTO.getProfileSource())) {
            throw new BusinessException(400, "画像来源编码不合法");
        }
        // 更新画像时技能标签清洗校验（在 updateByIdAndVersion 写 job_post 之前执行，不依赖事务回滚兜底）
        if (updateProfile) {
            profileDTO.setCoreSkills(jobProfileInputSanitizer.sanitize(profileDTO.getCoreSkills()));
            profileDTO.setSoftSkills(jobProfileInputSanitizer.sanitizeSoftSkills(profileDTO.getSoftSkills()));
        }

        // 5. 更新 job_post（乐观锁 version=request.version）
        JobPost update = new JobPost();
        update.setId(jobId);
        update.setCompanyId(companyId);
        update.setTitle(request.getTitle());
        update.setIndustryGroupCode(request.getIndustryGroupCode());
        update.setIndustryCode(request.getIndustryCode());
        update.setCityCode(request.getCityCode());
        // 城市名称以字典为准回填；未改城市时历史岗位放行（停用城市不拦截，见 resolveCityNameForUpdate）
        update.setCityName(resolveCityNameForUpdate(request.getCityCode(), post.getCityCode(), post.getCityName()));
        update.setMinExperienceYears(request.getMinExperienceYears() == null ? 0 : request.getMinExperienceYears());
        update.setEducationRequirement(educationRequirement);
        update.setSalaryMinAmount(request.getSalary().getMinAmount());
        update.setSalaryMaxAmount(request.getSalary().getMaxAmount());
        update.setSalaryCurrency(StringUtils.hasText(request.getSalary().getCurrency())
                ? request.getSalary().getCurrency() : "CNY");
        update.setSalaryPeriod(request.getSalary().getPeriod());
        update.setSalaryMonths(request.getSalary().getMonths());
        update.setIsSalaryNegotiable(Boolean.TRUE.equals(request.getSalary().getNegotiable()) ? 1 : 0);
        update.setSalaryRawText(request.getSalary().getRawText());
        update.setTotalHc(request.getTotalHc() == null ? 1 : request.getTotalHc());
        update.setJdText(request.getJdText());
        update.setJdSummary(buildJdSummary(request.getJdText()));
        update.setExpiresAt(request.getExpiresAt());
        update.setUpdatedBy(userId);
        update.setVersion(request.getVersion());
        if (jobPostMapper.updateByIdAndVersion(update) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }

        // 6. 更新画像（仅三件套齐全时）
        JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, companyId);
        Integer newProfileVersion;
        if (updateProfile) {
            // 更新画像（乐观锁 version=request.profileVersion，失败即版本冲突）
            JobProfile updateProfileEntity = new JobProfile();
            updateProfileEntity.setId(profile != null ? profile.getId() : null);
            updateProfileEntity.setCompanyId(companyId);
            updateProfileEntity.setJobId(jobId);
            updateProfileEntity.setJobType(profileDTO.getJobType());
            updateProfileEntity.setCoreSkills(JsonUtil.toJson(profileDTO.getCoreSkills()));
            updateProfileEntity.setSoftSkills(JsonUtil.toJson(profileDTO.getSoftSkills()));
            updateProfileEntity.setIndustryExperience(profileDTO.getIndustryExperience());
            updateProfileEntity.setHiddenRequirements(JsonUtil.toJson(profileDTO.getHiddenRequirements()));
            updateProfileEntity.setInterviewFocus(JsonUtil.toJson(profileDTO.getInterviewFocus()));
            updateProfileEntity.setProfileSource(profileDTO.getProfileSource());
            updateProfileEntity.setVersion(request.getProfileVersion());
            if (Boolean.TRUE.equals(request.getProfileConfirmed())) {
                updateProfileEntity.setConfirmedBy(userId);
                updateProfileEntity.setConfirmedAt(LocalDateTime.now());
            }
            if (updateProfileEntity.getId() == null
                    || jobProfileMapper.updateByIdAndVersion(updateProfileEntity) == 0) {
                throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
            }
            newProfileVersion = request.getProfileVersion() + 1;
        } else {
            // 不改画像：返回当前画像版本
            newProfileVersion = profile != null ? profile.getVersion() : null;
        }

        // 6. 返回更新后数据
        JobUpdateResponse resp = new JobUpdateResponse();
        resp.setJobId(jobId);
        resp.setStatus(JobStatus.DRAFT.getCode());
        resp.setVersion(request.getVersion() + 1);
        resp.setProfileVersion(newProfileVersion);
        resp.setUpdatedAt(LocalDateTime.now());
        return resp;
    }

    @Override
    public void deleteJob(Long jobId, Integer version) {
        Long companyId = requireCompanyId();
        if (version == null) {
            throw new BusinessException(400, "岗位版本不能为空");
        }

        // 先查存在性与可删除状态（企业隔离）
        JobPost post = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        if (!JobStatus.DRAFT.getCode().equals(post.getStatus())) {
            throw new BusinessException(JobErrorCode.JOB_NOT_DELETABLE);
        }

        // 乐观锁软删除
        if (jobPostMapper.softDelete(jobId, companyId, version) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobStatusResponse changeJobStatus(Long jobId, JobStatusActionRequest request) {
        Long companyId = requireCompanyId();

        // 0. 操作人必填且非 0（0=SYSTEM 保留语义，HR 操作不得写 operator_id=0 的矛盾审计行）
        Long hrUserId = UserContext.getUserId();
        if (hrUserId == null) {
            throw new BusinessException(400, "缺少操作人");
        }
        if (hrUserId <= 0) {
            throw new BusinessException(400, "操作人ID非法");
        }

        // 1. 企业隔离查询
        JobPost post = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }

        // 2. 状态机迁移校验
        int availableHc = post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc();
        JobStateValidator.validateTransition(post.getStatus(), request.getAction(), post.getCloseReason(), availableHc);

        // 3. 构造乐观锁更新对象（id/companyId/version=request.version）
        JobPost op = new JobPost();
        op.setId(jobId);
        op.setCompanyId(companyId);
        op.setVersion(request.getVersion());

        JobStatusResponse resp = new JobStatusResponse();
        resp.setJobId(jobId);
        resp.setVersion(request.getVersion() + 1);

        String action = request.getAction();
        if ("PUBLISH".equals(action)) {
            // PUBLISH 额外校验：画像版本一致 + 发布前置 8 项
            if (request.getProfileVersion() == null) {
                throw new BusinessException(400, "发布岗位必须提供画像版本");
            }
            JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, companyId);
            if (profile == null || !request.getProfileVersion().equals(profile.getVersion())) {
                throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
            }
            validatePublishRequirements(post, profile);
            if (jobPostMapper.publish(op) == 0) {
                throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
            }
            writeStatusLog(post, JobStatus.PUBLISHED.getCode(), "MANUAL_PUBLISH", hrUserId);
            resp.setStatus(JobStatus.PUBLISHED.getCode());
            resp.setPublishedAt(LocalDateTime.now());
            return resp;
        }
        if ("CLOSE".equals(action)) {
            if (jobPostMapper.close(op) == 0) {
                throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
            }
            writeStatusLog(post, JobStatus.CLOSED.getCode(), "MANUAL", hrUserId);
            resp.setStatus(JobStatus.CLOSED.getCode());
            resp.setClosedAt(LocalDateTime.now());
            resp.setCloseReason(CloseReasonEnum.MANUAL.getCode());
            return resp;
        }
        // REOPEN：validator 已保证 CLOSED(HC_CONFIRMED_FULL) 且 availableHc>0
        if (jobPostMapper.reopen(op) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        writeStatusLog(post, JobStatus.PUBLISHED.getCode(), "REOPEN", hrUserId);
        resp.setStatus(JobStatus.PUBLISHED.getCode());
        resp.setPublishedAt(LocalDateTime.now());
        resp.setCloseReason(null);
        resp.setClosedAt(null);
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<JobStatusLogVO> getStatusHistory(Long jobId, Integer page, Integer size) {
        Long companyId = requireCompanyId();

        // 企业隔离：岗位必须属于当前企业
        JobPost post = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }

        // 分页边界收敛（对齐 listAdminJobs 风格，不抛 400）
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PageHelper.startPage(safePage, safeSize);
        // 双条件过滤（company_id+job_id）：复用 idx_job_id 索引 + 查询层企业隔离
        List<JobStatusLog> logs = jobStatusLogMapper.selectByJobIdAndCompanyId(companyId, jobId);
        PageInfo<JobStatusLog> pageInfo = new PageInfo<>(logs);
        List<JobStatusLogVO> voList = logs.stream().map(this::toStatusLogVO).collect(Collectors.toList());
        return PageResult.of(voList, pageInfo.getTotal(), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobOptionsResponse.CityOption> listCityOptions() {
        // HR 下拉取全部启用城市（不受岗位是否存在影响），名称以字典为准
        return jobCityDictMapper.selectEnabledAll().stream()
                .map(city -> new JobOptionsResponse.CityOption(city.getCode(), city.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 批量查用户信息（只调一次 batch，不逐 id 单查——用户服务异常时不放大请求）：
     * 返回 Map&lt;userId, DTO&gt;；失败/缺失/返回空 → 空 Map，调用方按 "用户"+id 兜底。
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
            log.warn("批量查询用户姓名失败，降级为 \"用户\"+id: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 创建人姓名：真实姓名优先；用户缺失/服务不可用时降级为 "用户"+id（对齐 lingxi-hr 兜底文案）
     */
    private String resolveCreatorName(Long createdBy, Map<Long, UserInfoDTO> userMap) {
        if (createdBy == null) {
            return null;
        }
        UserInfoDTO user = userMap.get(createdBy);
        return user != null && user.getName() != null ? user.getName() : "用户" + createdBy;
    }

    /**
     * 写岗位状态变更审计日志（HR 操作：发布/手动关闭/重开，同事务）
     * <p>必须在同步 DB 后、返回前调用（此时 post 仍为变更前状态，fromStatus 正确）。
     * 复用落库模板：insert 非 1 行抛异常回滚同事务（杜绝"已变更无审计"）。</p>
     */
    private void writeStatusLog(JobPost post, String toStatus, String reason, Long operatorId) {
        JobStatusLog statusLog = new JobStatusLog();
        statusLog.setCompanyId(post.getCompanyId());
        statusLog.setJobId(post.getId());
        statusLog.setFromStatus(post.getStatus());
        statusLog.setToStatus(toStatus);
        statusLog.setReason(reason);
        statusLog.setOperatorId(operatorId);
        statusLog.setOperatorRole("HR");
        if (jobStatusLogMapper.insert(statusLog) != 1) {
            throw new IllegalStateException("岗位状态变更审计日志插入失败, jobId=" + post.getId());
        }
    }

    /**
     * JobStatusLog → VO（不暴露内部实体）
     */
    private JobStatusLogVO toStatusLogVO(JobStatusLog log) {
        JobStatusLogVO vo = new JobStatusLogVO();
        vo.setId(log.getId());
        vo.setFromStatus(log.getFromStatus());
        vo.setToStatus(log.getToStatus());
        vo.setReason(log.getReason());
        vo.setReasonDetail(log.getReasonDetail());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorRole(log.getOperatorRole());
        vo.setRequestId(log.getRequestId());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    @Override
    public PageResult<HrJobListVO> listJobs(Map<String, Object> query) {
        Long companyId = requireCompanyId();
        int page = query.get("page") == null ? 1 : Integer.parseInt(String.valueOf(query.get("page")));
        int size = query.get("size") == null ? 20 : Integer.parseInt(String.valueOf(query.get("size")));
        // 显式收敛分页边界：page>=1，size 1~50（不依赖 PageHelper）
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 50);

        // companyId 强制从 UserContext 取，不信任前端传入
        Map<String, Object> q = new HashMap<>();
        q.put("companyId", companyId);
        q.put("status", query.get("status"));

        PageHelper.startPage(page, size);
        List<HrJobListVO> list = jobPostMapper.selectHrList(q);
        PageInfo<HrJobListVO> pageInfo = new PageInfo<>(list);

        // 组装嵌套薪资 + 解析技能标签，清空承接字段
        for (HrJobListVO vo : list) {
            fillListSalary(vo);
            vo.setSkillTags(parseSkillTags(vo.getSkillTagsRaw()));
            vo.setSkillTagsRaw(null);
        }
        // 创建人姓名：本页去重收集创建人ID，一次批量查询（展示增强，失败降级 "用户"+id 不阻断列表）
        List<Long> creatorIds = list.stream()
                .map(HrJobListVO::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserInfoDTO> userMap = fetchUsers(creatorIds);
        for (HrJobListVO vo : list) {
            vo.setCreatedByName(resolveCreatorName(vo.getCreatedBy(), userMap));
        }
        return PageResult.of(list, pageInfo.getTotal(), page, size);
    }

    @Override
    public HrJobDetailVO getHrJobDetail(Long jobId) {
        Long companyId = requireCompanyId();
        // 企业隔离查询：不属于本企业 → 2101（不泄露其他企业岗位状态）
        JobPost post = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, companyId);

        HrJobDetailVO vo = new HrJobDetailVO();
        vo.setJobId(post.getId());
        vo.setCompanyId(post.getCompanyId());
        vo.setTitle(post.getTitle());
        vo.setStatus(post.getStatus());
        vo.setIndustryGroupCode(post.getIndustryGroupCode());
        vo.setIndustryCode(post.getIndustryCode());
        vo.setCityCode(post.getCityCode());
        vo.setCityName(post.getCityName());
        vo.setMinExperienceYears(post.getMinExperienceYears());
        vo.setEducationRequirement(post.getEducationRequirement());
        vo.setSalary(buildSalaryVO(post));
        vo.setTotalHc(post.getTotalHc());
        vo.setReservedHc(post.getReservedHc());
        vo.setConfirmedHc(post.getConfirmedHc());
        vo.setAvailableHc(post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc());
        vo.setJdText(post.getJdText());
        vo.setJdSummary(post.getJdSummary());
        vo.setPauseReason(post.getPauseReason());
        vo.setCloseReason(post.getCloseReason());
        vo.setPublishedAt(post.getPublishedAt());
        vo.setClosedAt(post.getClosedAt());
        vo.setExpiresAt(post.getExpiresAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());
        // 创建人：复用列表同款批量查询（单元素），不维护第二套单查逻辑
        vo.setCreatedBy(post.getCreatedBy());
        vo.setCreatedByName(resolveCreatorName(post.getCreatedBy(), fetchUsers(
                post.getCreatedBy() == null ? Collections.emptyList()
                        : Collections.singletonList(post.getCreatedBy()))));
        vo.setVersion(post.getVersion());

        if (profile != null) {
            vo.setJobType(profile.getJobType());
            vo.setCoreSkills(parseCoreSkills(profile.getCoreSkills()));
            vo.setSoftSkills(parseSoftSkills(profile.getSoftSkills()));
            vo.setIndustryExperience(profile.getIndustryExperience());
            vo.setHiddenRequirements(parseHiddenRequirements(profile.getHiddenRequirements()));
            vo.setInterviewFocus(parseInterviewFocus(profile.getInterviewFocus()));
            vo.setProfileSource(profile.getProfileSource());
            vo.setConfirmedBy(profile.getConfirmedBy());
            vo.setConfirmedAt(profile.getConfirmedAt());
            vo.setProfileConfirmed(profile.getConfirmedAt() != null);
            vo.setProfileVersion(profile.getVersion());
        } else {
            vo.setProfileConfirmed(false);
        }
        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当前企业ID，非企业用户抛业务异常
     */
    private Long requireCompanyId() {
        Long companyId = UserContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException(403, "非企业用户");
        }
        return companyId;
    }

    /**
     * 行业校验：一级行业必须合法且与具体行业一致
     */
    private void validateIndustry(String industryGroupCode, String industryCode) {
        if (!IndustryCodeEnum.isValid(industryGroupCode) || !industryGroupCode.equals(industryCode)) {
            throw new BusinessException(400, "行业编码不合法或与行业分组不一致");
        }
    }

    /**
     * 学历校验：默认 NONE，非法抛 400
     */
    private String resolveEducation(String educationRequirement) {
        if (!StringUtils.hasText(educationRequirement)) {
            educationRequirement = EducationRequirementEnum.NONE.getCode();
        }
        if (!EducationRequirementEnum.isValid(educationRequirement)) {
            throw new BusinessException(400, "学历要求编码不合法");
        }
        return educationRequirement;
    }

    /**
     * 薪资校验：先统一校验周期（面议也必须合法），再按是否面议处理金额与月数
     */
    private void validateSalary(JobCreateRequest.SalaryDTO salary) {
        // 所有薪资（含面议）都必须有合法周期，避免面议薪资绕过周期校验
        if (!StringUtils.hasText(salary.getPeriod()) || !SalaryPeriodEnum.isValid(salary.getPeriod())) {
            throw new BusinessException(400, "薪资周期不能为空且必须合法");
        }
        boolean negotiable = Boolean.TRUE.equals(salary.getNegotiable());
        if (negotiable) {
            salary.setMinAmount(null);
            salary.setMaxAmount(null);
            salary.setMonths(null);
            return;
        }
        if (salary.getMinAmount() == null || salary.getMaxAmount() == null || salary.getMonths() == null) {
            throw new BusinessException(400, "非面议薪资必须提供最低薪资、最高薪资和年薪月数");
        }
        if (salary.getMinAmount() > salary.getMaxAmount()) {
            throw new BusinessException(400, "最低薪资不能高于最高薪资");
        }
        if (salary.getMonths() < 1 || salary.getMonths() > 24) {
            throw new BusinessException(400, "年薪月数需在1~24之间");
        }
    }

    /**
     * 创建岗位城市校验与回填（严格）：编码不存在 → 2107；已停用 → 2108；否则返回字典名称
     */
    private String resolveCityName(String cityCode) {
        JobCityDict city = jobCityDictMapper.selectByCode(cityCode);
        if (city == null) {
            throw new BusinessException(JobErrorCode.CITY_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(city.getStatus())) {
            throw new BusinessException(JobErrorCode.CITY_DISABLED);
        }
        return city.getName();
    }

    /**
     * 更新岗位城市校验与回填：
     * <ul>
     *   <li>未改城市（请求编码 == 岗位原编码）：历史岗位放行——字典存在则按字典回填（含停用城市，名称以字典为准），
     *       字典不存在（异常存量数据）保留原展示名，不受城市停用影响，可继续编辑 JD 等；</li>
     *   <li>改城市：与创建一致严格校验（不存在 → 2107，已停用 → 2108）。</li>
     * </ul>
     */
    private String resolveCityNameForUpdate(String cityCode, String originCityCode, String originCityName) {
        JobCityDict city = jobCityDictMapper.selectByCode(cityCode);
        if (Objects.equals(cityCode, originCityCode)) {
            return city != null ? city.getName() : originCityName;
        }
        if (city == null) {
            throw new BusinessException(JobErrorCode.CITY_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(city.getStatus())) {
            throw new BusinessException(JobErrorCode.CITY_DISABLED);
        }
        return city.getName();
    }

    /**
     * 组装岗位实体（status=DRAFT、version=0）
     */
    private JobPost buildJobPost(JobCreateRequest request, Long companyId, Long userId,
                                 String educationRequirement) {
        JobCreateRequest.SalaryDTO salary = request.getSalary();
        boolean negotiable = Boolean.TRUE.equals(salary.getNegotiable());

        JobPost post = new JobPost();
        post.setCompanyId(companyId);
        post.setTitle(request.getTitle());
        post.setIndustryGroupCode(request.getIndustryGroupCode());
        post.setIndustryCode(request.getIndustryCode());
        post.setCityCode(request.getCityCode());
        // 城市名称以字典为准回填，不信任请求中的 cityName（城市主数据唯一真值）
        post.setCityName(resolveCityName(request.getCityCode()));
        post.setMinExperienceYears(request.getMinExperienceYears() == null ? 0 : request.getMinExperienceYears());
        post.setEducationRequirement(educationRequirement);
        post.setSalaryMinAmount(salary.getMinAmount());
        post.setSalaryMaxAmount(salary.getMaxAmount());
        post.setSalaryCurrency(StringUtils.hasText(salary.getCurrency()) ? salary.getCurrency() : "CNY");
        post.setSalaryPeriod(salary.getPeriod());
        post.setSalaryMonths(salary.getMonths());
        post.setIsSalaryNegotiable(negotiable ? 1 : 0);
        post.setSalaryRawText(salary.getRawText());
        post.setTotalHc(request.getTotalHc() == null ? 1 : request.getTotalHc());
        post.setJdText(request.getJdText());
        post.setJdSummary(buildJdSummary(request.getJdText()));
        post.setStatus("DRAFT");
        post.setVersion(0);
        post.setCreatedBy(userId);
        post.setUpdatedBy(userId);
        post.setExpiresAt(request.getExpiresAt());
        // 显式写入创建/更新时间，保证响应 createdAt 有值（数据库默认值不会回填到实体）
        LocalDateTime now = LocalDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        return post;
    }

    /**
     * 组装岗位画像实体（version=1，profileConfirmed=true 时写确认信息）
     */
    private JobProfile buildJobProfile(JobCreateRequest request, JobCreateRequest.JobProfileDTO profileDTO,
                                       Long companyId, Long jobId, Long userId) {
        JobProfile profile = new JobProfile();
        profile.setCompanyId(companyId);
        profile.setJobId(jobId);
        profile.setJobType(profileDTO.getJobType());
        profile.setCoreSkills(JsonUtil.toJson(profileDTO.getCoreSkills()));
        profile.setSoftSkills(JsonUtil.toJson(profileDTO.getSoftSkills()));
        profile.setIndustryExperience(profileDTO.getIndustryExperience());
        profile.setHiddenRequirements(JsonUtil.toJson(profileDTO.getHiddenRequirements()));
        profile.setInterviewFocus(JsonUtil.toJson(profileDTO.getInterviewFocus()));
        profile.setProfileSource(profileDTO.getProfileSource());
        profile.setVersion(1);
        if (Boolean.TRUE.equals(request.getProfileConfirmed())) {
            profile.setConfirmedBy(userId);
            profile.setConfirmedAt(LocalDateTime.now());
        }
        return profile;
    }

    /**
     * 发布前置校验 8 项：title/cityCode/jobType/coreSkills/totalHc/jdText/薪资合法/画像已确认
     */
    private void validatePublishRequirements(JobPost post, JobProfile profile) {
        if (!StringUtils.hasText(post.getTitle())) {
            throw new BusinessException(400, "发布失败：岗位名称不能为空");
        }
        if (!StringUtils.hasText(post.getCityCode())) {
            throw new BusinessException(400, "发布失败：城市编码不能为空");
        }
        if (!StringUtils.hasText(profile.getJobType())) {
            throw new BusinessException(400, "发布失败：岗位类型不能为空");
        }
        // 核心技能必须至少一项：仅判断 hasText 无法拦住 "[]" / "null"，需反序列化后判空
        List<JobRequirementResponse.CoreSkill> coreSkills = parseCoreSkills(profile.getCoreSkills());
        if (coreSkills == null || coreSkills.isEmpty()) {
            throw new BusinessException(400, "发布失败：核心技能不能为空");
        }
        if (post.getTotalHc() == null || post.getTotalHc() < 1) {
            throw new BusinessException(400, "发布失败：总HC至少为1");
        }
        if (!StringUtils.hasText(post.getJdText())) {
            throw new BusinessException(400, "发布失败：JD内容不能为空");
        }
        // 薪资合法性：面议或金额合法，且周期必须存在且为合法编码
        boolean amountValid = Integer.valueOf(1).equals(post.getIsSalaryNegotiable())
                || (post.getSalaryMinAmount() != null && post.getSalaryMaxAmount() != null
                    && post.getSalaryMinAmount() <= post.getSalaryMaxAmount());
        boolean periodValid = StringUtils.hasText(post.getSalaryPeriod())
                && SalaryPeriodEnum.isValid(post.getSalaryPeriod());
        if (!amountValid || !periodValid) {
            throw new BusinessException(400, "发布失败：薪资信息不合法");
        }
        if (profile.getConfirmedAt() == null) {
            throw new BusinessException(400, "发布失败：岗位画像未确认");
        }
    }

    /**
     * 生成 JD 摘要（截前 N 字符，空则空串）
     */
    private String buildJdSummary(String jdText) {
        if (!StringUtils.hasText(jdText)) {
            return "";
        }
        return jdText.length() > JD_SUMMARY_MAX_LENGTH
                ? jdText.substring(0, JD_SUMMARY_MAX_LENGTH)
                : jdText;
    }

    /**
     * 列表项：扁平薪资字段组装成嵌套 SalaryVO 后清空扁平字段
     */
    private void fillListSalary(HrJobListVO vo) {
        HrJobListVO.SalaryVO salary = new HrJobListVO.SalaryVO();
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
     * 详情：岗位薪资字段组装成嵌套 SalaryVO
     */
    private HrJobDetailVO.SalaryVO buildSalaryVO(JobPost post) {
        HrJobDetailVO.SalaryVO salary = new HrJobDetailVO.SalaryVO();
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
     * 解析 core_skills JSON → 结构化核心技能列表
     */
    private List<JobRequirementResponse.CoreSkill> parseCoreSkills(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<JobRequirementResponse.CoreSkill>>() {});
        } catch (Exception e) {
            log.warn("解析 core_skills 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 soft_skills JSON → 结构化软能力列表
     */
    private List<JobRequirementResponse.SoftSkill> parseSoftSkills(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<JobRequirementResponse.SoftSkill>>() {});
        } catch (Exception e) {
            log.warn("解析 soft_skills 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 hidden_requirements JSON → 结构化隐性要求列表
     */
    private List<JobCreateRequest.HiddenRequirement> parseHiddenRequirements(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<JobCreateRequest.HiddenRequirement>>() {});
        } catch (Exception e) {
            log.warn("解析 hidden_requirements 失败, 返回空列表: {}", e.getMessage());
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
}

package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.domain.dto.request.HcConfirmRequest;
import com.lingxi.job.domain.dto.request.HcReleaseRequest;
import com.lingxi.job.domain.dto.request.HcReserveRequest;
import com.lingxi.job.domain.dto.response.HcConfirmResponse;
import com.lingxi.job.domain.dto.response.HcFlowResponse;
import com.lingxi.job.domain.dto.response.HcReleaseResponse;
import com.lingxi.job.domain.dto.response.HcReserveResponse;
import com.lingxi.job.domain.dto.response.InternalJobValidationResponse;
import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import com.lingxi.job.domain.entity.JobHcReservation;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.entity.JobStatusLog;
import com.lingxi.job.domain.vo.InternalCompanyJobVO;
import com.lingxi.job.domain.vo.InternalJobCardVO;
import com.lingxi.job.enums.CloseReasonEnum;
import com.lingxi.job.enums.PauseReasonEnum;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobHcReservationMapper;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.mapper.JobStatusLogMapper;
import com.lingxi.job.service.InternalJobService;
import org.springframework.beans.BeanUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位内部接口服务实现
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalJobServiceImpl implements InternalJobService {

    private final JobPostMapper jobPostMapper;
    private final JobProfileMapper jobProfileMapper;
    private final JobHcReservationMapper hcReservationMapper;
    private final JobStatusLogMapper jobStatusLogMapper;

    /** HC 释放原因白名单 */
    private static final Set<String> RELEASE_REASONS = new HashSet<>(Arrays.asList(
            "REJECTED", "EXPIRED", "NOT_ONBOARDED", "D_PERSIST_FAILED"));

    @Override
    public InternalJobValidationResponse getJobForValidation(Long jobId) {
        JobPost post = jobPostMapper.selectById(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        int total = post.getTotalHc();
        int reserved = post.getReservedHc();
        int confirmed = post.getConfirmedHc();
        int available = total - reserved - confirmed;
        boolean deleted = post.getDeletedAt() != null;
        boolean canApply = "PUBLISHED".equals(post.getStatus()) && !deleted && available > 0;

        InternalJobValidationResponse resp = new InternalJobValidationResponse();
        resp.setJobId(post.getId());
        resp.setCompanyId(post.getCompanyId());
        resp.setTitle(post.getTitle());
        resp.setStatus(post.getStatus());
        resp.setDeleted(deleted);
        resp.setTotalHc(total);
        resp.setReservedHc(reserved);
        resp.setConfirmedHc(confirmed);
        resp.setAvailableHc(available);
        resp.setCanApply(canApply);
        resp.setSalaryMinAmount(post.getSalaryMinAmount());
        resp.setSalaryMaxAmount(post.getSalaryMaxAmount());
        resp.setSalaryNegotiable(post.getIsSalaryNegotiable() != null && post.getIsSalaryNegotiable() == 1);

        // 岗位画像信息（用于匹配计算）
        resp.setEducationRequirement(post.getEducationRequirement());
        resp.setMinExperienceYears(post.getMinExperienceYears());
        resp.setJdText(post.getJdText());

        // 获取岗位画像
        JobProfile profile = jobProfileMapper.selectByJobId(jobId);
        if (profile != null) {
            Map<String, Object> profileMap = new HashMap<>();
            profileMap.put("jobType", profile.getJobType());
            profileMap.put("coreSkills", parseCoreSkillsAsMap(profile.getCoreSkills()));
            profileMap.put("softSkills", parseSoftSkillsAsMap(profile.getSoftSkills()));
            profileMap.put("industryExperience", profile.getIndustryExperience());
            resp.setProfile(profileMap);
        }

        return resp;
    }

    /**
     * 解析核心技能JSON为Map列表
     */
    private List<Map<String, Object>> parseCoreSkillsAsMap(String coreSkillsJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (coreSkillsJson == null || coreSkillsJson.isEmpty()) return result;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> skills = mapper.readValue(coreSkillsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            return skills;
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * 解析软技能JSON为Map列表
     */
    private List<Map<String, Object>> parseSoftSkillsAsMap(String softSkillsJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (softSkillsJson == null || softSkillsJson.isEmpty()) return result;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> skills = mapper.readValue(softSkillsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            return skills;
        } catch (Exception e) {
            return result;
        }
    }

    @Override
    public PageResult<InternalJobCardVO> searchJobs(Map<String, Object> query) {
        int page = query.get("page") == null ? 1 : Integer.parseInt(String.valueOf(query.get("page")));
        int size = query.get("size") == null ? 20 : Integer.parseInt(String.valueOf(query.get("size")));
        // 显式收敛分页边界：page>=1，size 1~50（不依赖 PageHelper）
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 50);

        // PageHelper 分页必须在 Mapper 查询前调用
        PageHelper.startPage(page, size);
        List<InternalJobCardVO> list = jobPostMapper.selectForInternalSearch(query);
        PageInfo<InternalJobCardVO> pageInfo = new PageInfo<>(list);

        // 解析 skillTagsRaw → skillTags，并清空原始字段
        for (InternalJobCardVO vo : list) {
            vo.setSkillTags(parseSkillTags(vo.getSkillTagsRaw()));
            vo.setSkillTagsRaw(null);
        }
        return PageResult.of(list, pageInfo.getTotal(), page, size);
    }

    @Override
    public JobRequirementResponse getJobRequirements(Long jobId) {
        JobPost post = jobPostMapper.selectById(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        JobProfile profile = jobProfileMapper.selectByJobId(jobId);
        // 阶段1 近似判断：画像未确认 → code:2003（阶段2 引入 profileConfirmed 语义后复核）
        if (profile == null || !StringUtils.hasText(profile.getJobType())) {
            throw new BusinessException(JobErrorCode.PROFILE_NOT_CONFIRMED);
        }

        JobRequirementResponse resp = new JobRequirementResponse();
        resp.setJobId(jobId);
        resp.setJobType(profile.getJobType());
        resp.setCoreSkills(parseCoreSkills(profile.getCoreSkills()));
        resp.setSoftSkills(parseSoftSkills(profile.getSoftSkills()));
        resp.setIndustryExperience(profile.getIndustryExperience());
        resp.setInterviewFocus(parseInterviewFocus(profile.getInterviewFocus()));
        resp.setProfileConfirmed(true);
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalCompanyJobVO> listCompanyJobs(Long companyId) {
        // 企业隔离与软删除过滤由 SQL 固化，Service 不做状态过滤/分页
        return jobPostMapper.selectByCompanyId(companyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HcReserveResponse reserveHc(Long jobId, HcReserveRequest request) {
        JobPost post = jobPostMapper.selectByIdForUpdate(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        // 校验请求 companyId 与岗位所属企业一致
        if (!Objects.equals(post.getCompanyId(), request.getCompanyId())) {
            throw new BusinessException(JobErrorCode.HC_IDEMPOTENT_MISMATCH);
        }
        // 幂等：按 company+offer 查已有流水
        JobHcReservation exist = hcReservationMapper.selectByCompanyAndOfferForUpdate(
                request.getCompanyId(), request.getOfferId());
        if (exist != null) {
            if (!Objects.equals(exist.getJobId(), jobId)
                    || !Objects.equals(exist.getCandidateId(), request.getCandidateId())) {
                throw new BusinessException(JobErrorCode.HC_IDEMPOTENT_MISMATCH);
            }
            return buildReserveResponse(post, exist);
        }
        // 岗位状态校验
        if (!"PUBLISHED".equals(post.getStatus())) {
            throw new BusinessException(JobErrorCode.HC_ILLEGAL_STATE);
        }
        int available = post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc();
        if (available <= 0) {
            throw new BusinessException(JobErrorCode.HC_NOT_AVAILABLE);
        }
        // 建流水
        JobHcReservation reservation = new JobHcReservation();
        reservation.setCompanyId(request.getCompanyId());
        reservation.setJobId(jobId);
        reservation.setOfferId(request.getOfferId());
        reservation.setCandidateId(request.getCandidateId());
        reservation.setStatus("RESERVED");
        reservation.setReservedAt(LocalDateTime.now());
        reservation.setVersion(0);
        hcReservationMapper.insert(reservation);
        // 更新岗位计数 + 状态自动管理（一次 UPDATE，乐观锁）：reservedHc+1，confirmedHc 保持原值
        int newReserved = post.getReservedHc() + 1;
        int newConfirmed = post.getConfirmedHc();
        int newAvailable = post.getTotalHc() - newReserved - newConfirmed;
        String targetStatus = post.getStatus();
        String targetPauseReason = post.getPauseReason();
        boolean changed = false;
        if (newAvailable == 0 && "PUBLISHED".equals(post.getStatus())) {
            // 预占满 → 自动暂停（三态字段写死，防脏数据残留）
            targetStatus = "PAUSED";
            targetPauseReason = PauseReasonEnum.HC_RESERVED_FULL.getCode();
            changed = true;
        }
        JobPost update = buildHcUpdate(post, newReserved, newConfirmed,
                targetStatus, targetPauseReason, null, null, post.getPublishedAt());
        if (jobPostMapper.updateHcAndStatus(update) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        if (changed) {
            // 此时 post 仍为变更前状态，审计 fromStatus 正确
            writeStatusLog(post, targetStatus, PauseReasonEnum.HC_RESERVED_FULL.getCode());
        }
        // 同步更新内存对象，使响应返回更新后数据
        post.setReservedHc(newReserved);
        post.setStatus(targetStatus);
        post.setPauseReason(targetPauseReason);
        post.setCloseReason(null);
        post.setClosedAt(null);
        post.setVersion(post.getVersion() + 1);
        return buildReserveResponse(post, reservation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HcConfirmResponse confirmHc(Long jobId, HcConfirmRequest request) {
        // 统一加锁顺序：先锁岗位，再锁流水（与 reserve 一致，避免死锁）
        JobPost post = jobPostMapper.selectByIdForUpdate(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        // 校验请求 companyId 与岗位所属企业一致
        if (!Objects.equals(post.getCompanyId(), request.getCompanyId())) {
            throw new BusinessException(JobErrorCode.HC_IDEMPOTENT_MISMATCH);
        }
        // 按 company+job+offer 查流水并加锁（防操作错岗位）
        JobHcReservation reservation = hcReservationMapper.selectByCompanyJobOfferForUpdate(
                request.getCompanyId(), jobId, request.getOfferId());
        if (reservation == null) {
            throw new BusinessException(JobErrorCode.HC_RESERVATION_NOT_FOUND);
        }
        // 状态机校验
        if ("RELEASED".equals(reservation.getStatus())) {
            throw new BusinessException(JobErrorCode.HC_ILLEGAL_STATE);
        }
        // CONFIRMED 幂等返回
        if ("CONFIRMED".equals(reservation.getStatus())) {
            return buildConfirmResponse(post, reservation);
        }
        // 更新流水状态
        JobHcReservation updateRes = new JobHcReservation();
        updateRes.setId(reservation.getId());
        updateRes.setVersion(reservation.getVersion());
        updateRes.setStatus("CONFIRMED");
        updateRes.setConfirmedAt(LocalDateTime.now());
        if (hcReservationMapper.updateStatus(updateRes) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        // 更新岗位计数 + 状态自动管理（一次 UPDATE，乐观锁）：reservedHc-1, confirmedHc+1
        int newReserved = post.getReservedHc() - 1;
        int newConfirmed = post.getConfirmedHc() + 1;
        String targetStatus = post.getStatus();
        String targetPauseReason = post.getPauseReason();
        String targetCloseReason = post.getCloseReason();
        LocalDateTime targetClosedAt = post.getClosedAt();
        boolean changed = false;
        if (newConfirmed == post.getTotalHc() && !"CLOSED".equals(post.getStatus())) {
            // 确认后正式 HC 满 → 自动关闭（三态字段写死；已 CLOSED 只改计数不覆盖）
            targetStatus = "CLOSED";
            targetPauseReason = null;
            targetCloseReason = CloseReasonEnum.HC_CONFIRMED_FULL.getCode();
            targetClosedAt = LocalDateTime.now();
            changed = true;
        }
        JobPost update = buildHcUpdate(post, newReserved, newConfirmed,
                targetStatus, targetPauseReason, targetCloseReason, targetClosedAt, post.getPublishedAt());
        if (jobPostMapper.updateHcAndStatus(update) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        if (changed) {
            // 此时 post 仍为变更前状态，审计 fromStatus 正确
            writeStatusLog(post, targetStatus, CloseReasonEnum.HC_CONFIRMED_FULL.getCode());
        }
        // 同步更新内存对象，使响应返回更新后数据
        post.setReservedHc(newReserved);
        post.setConfirmedHc(newConfirmed);
        post.setStatus(targetStatus);
        post.setPauseReason(targetPauseReason);
        post.setCloseReason(targetCloseReason);
        post.setClosedAt(targetClosedAt);
        post.setVersion(post.getVersion() + 1);
        reservation.setStatus("CONFIRMED");
        return buildConfirmResponse(post, reservation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HcReleaseResponse releaseHc(Long jobId, HcReleaseRequest request) {
        // 校验释放原因枚举
        if (!RELEASE_REASONS.contains(request.getReason())) {
            throw new BusinessException(400, "释放原因非法");
        }
        // 统一加锁顺序：先锁岗位，再锁流水（与 reserve 一致，避免死锁）
        JobPost post = jobPostMapper.selectByIdForUpdate(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        // 校验请求 companyId 与岗位所属企业一致
        if (!Objects.equals(post.getCompanyId(), request.getCompanyId())) {
            throw new BusinessException(JobErrorCode.HC_IDEMPOTENT_MISMATCH);
        }
        // 按 company+job+offer 查流水并加锁（防操作错岗位）
        JobHcReservation reservation = hcReservationMapper.selectByCompanyJobOfferForUpdate(
                request.getCompanyId(), jobId, request.getOfferId());
        if (reservation == null) {
            throw new BusinessException(JobErrorCode.HC_RESERVATION_NOT_FOUND);
        }
        // RELEASED 幂等返回
        if ("RELEASED".equals(reservation.getStatus())) {
            return buildReleaseResponse(post, reservation);
        }
        // 记录释放前状态，用于计数回滚
        boolean wasReserved = "RESERVED".equals(reservation.getStatus());
        // 更新流水状态（保留原确认时间，避免破坏审计历史）
        JobHcReservation updateRes = new JobHcReservation();
        updateRes.setId(reservation.getId());
        updateRes.setVersion(reservation.getVersion());
        updateRes.setStatus("RELEASED");
        updateRes.setConfirmedAt(reservation.getConfirmedAt());
        updateRes.setReleasedAt(LocalDateTime.now());
        updateRes.setReleaseReason(request.getReason());
        if (hcReservationMapper.updateStatus(updateRes) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        // 计数回滚 + 状态自动管理（一次 UPDATE，乐观锁）：reservedHc-1（原 RESERVED）或 confirmedHc-1（原 CONFIRMED）
        int newReserved = wasReserved ? post.getReservedHc() - 1 : post.getReservedHc();
        int newConfirmed = wasReserved ? post.getConfirmedHc() : post.getConfirmedHc() - 1;
        int newAvailable = post.getTotalHc() - newReserved - newConfirmed;
        String targetStatus = post.getStatus();
        String targetPauseReason = post.getPauseReason();
        String targetCloseReason = post.getCloseReason();
        LocalDateTime targetClosedAt = post.getClosedAt();
        LocalDateTime targetPublishedAt = post.getPublishedAt();
        String logReason = null;
        boolean changed = false;
        if (newAvailable > 0) {
            // 恢复前置到期判断：expires_at 为 NULL 或未到期才恢复；已到期只回退计数（防 C 端窗口可见）
            boolean notExpired = post.getExpiresAt() == null
                    || post.getExpiresAt().isAfter(LocalDateTime.now());
            if (notExpired && "PAUSED".equals(post.getStatus())
                    && PauseReasonEnum.HC_RESERVED_FULL.getCode().equals(post.getPauseReason())) {
                // 释放预占恢复：目标 PUBLISHED 三态字段全套归零（防脏数据残留）；published_at 保持原值（临时暂停不刷新）
                targetStatus = "PUBLISHED";
                targetPauseReason = null;
                targetCloseReason = null;
                targetClosedAt = null;
                changed = true;
                logReason = request.getReason();
            } else if (notExpired && "CLOSED".equals(post.getStatus())
                    && CloseReasonEnum.HC_CONFIRMED_FULL.getCode().equals(post.getCloseReason())) {
                // 满额关闭重开：目标 PUBLISHED 三态字段全套归零（防脏数据残留）；published_at=NOW()（正式重开）
                targetStatus = "PUBLISHED";
                targetPauseReason = null;
                targetCloseReason = null;
                targetClosedAt = null;
                targetPublishedAt = LocalDateTime.now();
                changed = true;
                logReason = request.getReason();
            }
            // MANUAL/VIOLATION/EXPIRED 或已到期 → 不恢复，只回退计数
        }
        JobPost update = buildHcUpdate(post, newReserved, newConfirmed,
                targetStatus, targetPauseReason, targetCloseReason, targetClosedAt, targetPublishedAt);
        if (jobPostMapper.updateHcAndStatus(update) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        if (changed) {
            // 此时 post 仍为变更前状态，审计 fromStatus 正确
            writeStatusLog(post, targetStatus, logReason);
        }
        // 同步更新内存对象，使响应返回更新后数据
        post.setReservedHc(newReserved);
        post.setConfirmedHc(newConfirmed);
        post.setStatus(targetStatus);
        post.setPauseReason(targetPauseReason);
        post.setCloseReason(targetCloseReason);
        post.setClosedAt(targetClosedAt);
        post.setPublishedAt(targetPublishedAt);
        post.setVersion(post.getVersion() + 1);
        reservation.setStatus("RELEASED");
        return buildReleaseResponse(post, reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public HcFlowResponse getHcFlow(Long jobId, Long offerId) {
        // 系分 §21：流水查询键为 company_id + offer_id；先按 jobId 反查岗位拿 companyId
        JobPost post = jobPostMapper.selectById(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        JobHcReservation reservation = hcReservationMapper.selectByCompanyAndOffer(post.getCompanyId(), offerId);
        if (reservation == null) {
            throw new BusinessException(JobErrorCode.HC_RESERVATION_NOT_FOUND);
        }
        // 防御：流水挂到同企业其他岗位时（路径 jobId ≠ 流水 jobId），视为流水不存在，避免错岗位读取
        if (!Objects.equals(reservation.getJobId(), jobId)) {
            throw new BusinessException(JobErrorCode.HC_RESERVATION_NOT_FOUND);
        }
        HcFlowResponse resp = new HcFlowResponse();
        BeanUtils.copyProperties(reservation, resp);
        return resp;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 组装 HC 状态自动管理合一 UPDATE 对象（乐观锁 version 取 post 当前值）
     * <p>与 {@link JobPostMapper#updateHcAndStatus} 配套：一次原子更新计数+状态+原因+时间。</p>
     */
    private JobPost buildHcUpdate(JobPost post, int reserved, int confirmed, String status,
                                  String pauseReason, String closeReason, LocalDateTime closedAt,
                                  LocalDateTime publishedAt) {
        JobPost update = new JobPost();
        update.setId(post.getId());
        update.setVersion(post.getVersion());
        update.setReservedHc(reserved);
        update.setConfirmedHc(confirmed);
        update.setStatus(status);
        update.setPauseReason(pauseReason);
        update.setCloseReason(closeReason);
        update.setClosedAt(closedAt);
        update.setPublishedAt(publishedAt);
        return update;
    }

    /**
     * 写岗位状态变更审计日志（HC 状态自动管理专用）
     * <p>必须在同步内存对象之前调用（此时 post 仍为变更前状态，fromStatus 正确）。
     * 沿用 JobExpireCloseServiceImpl 模板：operator 0/SYSTEM；insert 非 1 行抛异常回滚同事务。</p>
     */
    private void writeStatusLog(JobPost post, String toStatus, String reason) {
        JobStatusLog statusLog = new JobStatusLog();
        statusLog.setCompanyId(post.getCompanyId());
        statusLog.setJobId(post.getId());
        statusLog.setFromStatus(post.getStatus());
        statusLog.setToStatus(toStatus);
        statusLog.setReason(reason);
        statusLog.setOperatorId(0L);        // 0=SYSTEM
        statusLog.setOperatorRole("SYSTEM");
        if (jobStatusLogMapper.insert(statusLog) != 1) {
            throw new IllegalStateException("HC 状态变更审计日志插入失败, jobId=" + post.getId());
        }
    }

    /**
     * 组装 HC 预冻结响应
     */
    private HcReserveResponse buildReserveResponse(JobPost post, JobHcReservation reservation) {
        HcReserveResponse resp = new HcReserveResponse();
        resp.setReservationStatus(reservation.getStatus());
        resp.setTotalHc(post.getTotalHc());
        resp.setReservedHc(post.getReservedHc());
        resp.setConfirmedHc(post.getConfirmedHc());
        resp.setAvailableHc(post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc());
        resp.setJobStatus(post.getStatus());
        resp.setJobVersion(post.getVersion());
        resp.setReservationVersion(reservation.getVersion());
        return resp;
    }

    /**
     * 组装 HC 确认占用响应
     */
    private HcConfirmResponse buildConfirmResponse(JobPost post, JobHcReservation reservation) {
        HcConfirmResponse resp = new HcConfirmResponse();
        resp.setReservationStatus(reservation.getStatus());
        resp.setTotalHc(post.getTotalHc());
        resp.setReservedHc(post.getReservedHc());
        resp.setConfirmedHc(post.getConfirmedHc());
        resp.setAvailableHc(post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc());
        resp.setJobStatus(post.getStatus());
        resp.setJobVersion(post.getVersion());
        resp.setReservationVersion(reservation.getVersion());
        return resp;
    }

    /**
     * 组装 HC 释放回退响应
     */
    private HcReleaseResponse buildReleaseResponse(JobPost post, JobHcReservation reservation) {
        HcReleaseResponse resp = new HcReleaseResponse();
        resp.setReservationStatus(reservation.getStatus());
        resp.setTotalHc(post.getTotalHc());
        resp.setReservedHc(post.getReservedHc());
        resp.setConfirmedHc(post.getConfirmedHc());
        resp.setAvailableHc(post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc());
        resp.setJobStatus(post.getStatus());
        resp.setJobVersion(post.getVersion());
        resp.setReservationVersion(reservation.getVersion());
        return resp;
    }

    /**
     * 解析核心技能 JSON → 技能名称列表
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
     * 解析 interview_focus JSON → 考察重点列表
     */
    private List<String> parseInterviewFocus(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 interview_focus 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}

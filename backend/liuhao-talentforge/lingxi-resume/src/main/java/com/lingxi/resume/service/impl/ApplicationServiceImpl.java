package com.lingxi.resume.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageRequest;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.enums.ApplicationStatus;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.SnowflakeIdUtil;
import com.lingxi.resume.domain.dto.ApplicationQuery;
import com.lingxi.resume.domain.dto.ApplyRequestDTO;
import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.domain.entity.ResumeApplication;
import com.lingxi.resume.domain.entity.ResumeStatusLog;
import com.lingxi.resume.domain.vo.ApplicationDetailVO;
import com.lingxi.resume.domain.vo.ApplicationTimelineVO;
import com.lingxi.resume.domain.vo.ApplicationTrendVO;
import com.lingxi.resume.domain.vo.ApplicationVO;
import com.lingxi.resume.domain.vo.InternalApplicationVO;
import com.lingxi.resume.exception.ResumeErrorCode;
import com.lingxi.resume.mapper.ResumeApplicationMapper;
import com.lingxi.resume.mapper.ResumeMapper;
import com.lingxi.resume.mapper.ResumeStatusLogMapper;
import com.lingxi.resume.mq.ApplicationEventProducer;
import com.lingxi.resume.feign.MatchFeignClient;
import com.lingxi.resume.feign.OfferFeignClient;
import com.lingxi.resume.feign.HrNotifyFeignClient;
import com.lingxi.resume.feign.dto.OfferAcceptRequest;
import com.lingxi.resume.feign.dto.OfferRejectRequest;
import com.lingxi.resume.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 投递服务实现
 *
 * <p>状态机核心 {@link #transition}：白名单校验 → 条件 UPDATE（乐观锁，rows=0 冲突）
 * → 状态日志（幂等键）→ 事务提交后发 MQ。
 *
 * <p>跨模块数据（岗位标题/企业名）通过单库 JOIN 实时获取，不冗余存储 B/D 模块字段。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    /** 状态流转白名单：fromStatus -> 允许的 toStatus 集合 */
    private static final Map<String, Set<String>> TRANSITION_MAP = new HashMap<>();

    /** 目标状态 -> 状态时间戳列名（Service 层白名单映射，禁止前端直传列名） */
    private static final Map<String, String> STATUS_TIMESTAMP_COLUMN = new HashMap<>();

    /** sortBy 白名单（防 SQL 注入：非白名单值忽略，退化为默认排序）；aiScore 暂不支持（DB 无对应列） */
    private static final Set<String> ALLOWED_SORT_COLUMNS = new HashSet<>(
            Arrays.asList("matchScore", "submittedAt"));

    static {
        TRANSITION_MAP.put("SUBMITTED", new HashSet<>(Arrays.asList("VIEWED", "SCREENED", "REJECTED", "WITHDRAWN")));
        TRANSITION_MAP.put("VIEWED", new HashSet<>(Arrays.asList("SCREENED", "REJECTED", "WITHDRAWN")));
        TRANSITION_MAP.put("SCREENED", new HashSet<>(Arrays.asList("INTERVIEWING", "REJECTED", "WITHDRAWN")));
        TRANSITION_MAP.put("INTERVIEWING", new HashSet<>(Arrays.asList("OFFERABLE", "REJECTED", "SCREENED")));
        // 面试安排后不可撤回：OFFERABLE 仅流向 OFFERED/REJECTED（白名单与 isWithdrawable 双重防御）
        TRANSITION_MAP.put("OFFERABLE", new HashSet<>(Arrays.asList("OFFERED", "REJECTED")));
        // OFFERED 为待回应过程态：候选人接受/拒绝进入终态；HR 撤回/Offer 过期回退可回到 OFFERABLE
        TRANSITION_MAP.put("OFFERED", new HashSet<>(Arrays.asList("OFFER_ACCEPTED", "OFFER_DECLINED", "OFFERABLE")));

        STATUS_TIMESTAMP_COLUMN.put("VIEWED", "viewed_at");
        STATUS_TIMESTAMP_COLUMN.put("SCREENED", "screened_at");
        STATUS_TIMESTAMP_COLUMN.put("INTERVIEWING", "interviewing_at");
        STATUS_TIMESTAMP_COLUMN.put("OFFERABLE", "offerable_at");
        STATUS_TIMESTAMP_COLUMN.put("OFFERED", "offered_at");
        STATUS_TIMESTAMP_COLUMN.put("REJECTED", "rejected_at");
        STATUS_TIMESTAMP_COLUMN.put("WITHDRAWN", "withdrawn_at");
        // OFFER_ACCEPTED/OFFER_DECLINED 无独立时间戳列（接受/拒绝时间在状态日志时间线中体现），
        // 不入 STATUS_TIMESTAMP_COLUMN：transition() 对无列状态跳过时间戳写入
    }

    private final ResumeApplicationMapper applicationMapper;
    private final ResumeStatusLogMapper statusLogMapper;
    private final ResumeMapper resumeMapper;
    private final ApplicationEventProducer eventProducer;
    private final MatchFeignClient matchFeignClient;
    private final OfferFeignClient offerFeignClient;
    private final HrNotifyFeignClient hrNotifyFeignClient;
    private final ObjectMapper objectMapper;

    // ==================== C端：一键投递 ====================

    @Override
    @Transactional
    public ApplicationVO submit(ApplyRequestDTO dto) {
        Long candidateId = getLoginUserId();

        // ① 简历校验：resumeId 为空 → 默认简历；否则校验归属 + 解析完成
        Resume resume = resolveResume(candidateId, dto.getResumeId());

        // ② 岗位校验：存在 + 未删除 + PUBLISHED（单库直查 job_post）
        if (applicationMapper.countOpenJob(dto.getJobId()) == 0) {
            log.warn("投递失败: 岗位不在招聘中, jobId={}, candidateId={}", dto.getJobId(), candidateId);
            throw new BusinessException(ResumeErrorCode.JOB_NOT_OPEN);
        }

        // ③ 防重复投递：进行中则拒绝，终态（撤回/淘汰/拒Offer）则重新激活
        ResumeApplication existing = applicationMapper.selectByJobAndCandidate(dto.getJobId(), candidateId);
        if (existing != null) {
            ApplicationStatus existingStatus = ApplicationStatus.fromCode(existing.getStatus());
            // 终态可重新投递：WITHDRAWN(已撤回) / REJECTED(已淘汰) / OFFER_DECLINED(已拒绝)
            if (existingStatus == ApplicationStatus.WITHDRAWN
                    || existingStatus == ApplicationStatus.REJECTED
                    || existingStatus == ApplicationStatus.OFFER_DECLINED) {
                // 重新激活为 SUBMITTED
                // 重算匹配度
                BigDecimal reapplyMatchScore = null;
                try {
                    Map<String, Long> matchParams = new HashMap<>();
                    matchParams.put("jobId", dto.getJobId());
                    matchParams.put("userId", candidateId);
                    Result<Integer> matchResult = matchFeignClient.calculateMatchScore(matchParams);
                    if (matchResult != null && matchResult.getData() != null) {
                        reapplyMatchScore = new BigDecimal(matchResult.getData());
                    }
                } catch (Exception e) {
                    log.warn("重新投递: 计算匹配度失败, applicationId={}", existing.getId(), e);
                }
                int updated = applicationMapper.reapply(existing.getId(), resume.getId(), reapplyMatchScore);
                if (updated == 0) {
                    log.error("重新投递失败: UPDATE 影响 0 行, applicationId={}, 可能被并发删除",
                            existing.getId());
                    throw new BusinessException(ResumeErrorCode.APPLICATION_NOT_FOUND);
                }
                writeStatusLog(existing.getId(), existing.getStatus(), ApplicationStatus.SUBMITTED.getCode(),
                        candidateId, "CANDIDATE", "重新投递");
                registerAfterCommit(() -> eventProducer.sendNewApplication(
                        existing.getId(), candidateId, dto.getJobId()));
                registerAfterCommit(() -> notifyHrNewApplication(existing.getId()));
                log.info("重新投递成功: applicationId={}, jobId={}, candidateId={}, resumeId={}, fromStatus={}",
                        existing.getId(), dto.getJobId(), candidateId, resume.getId(), existingStatus.getCode());
                // 返回投递结果
                ApplicationVO vo = new ApplicationVO();
                vo.setId(existing.getId());
                vo.setJobId(dto.getJobId());
                vo.setStatus(ApplicationStatus.SUBMITTED.getCode());
                ResumeApplication full = applicationMapper.selectByIdWithJob(existing.getId());
                if (full != null) {
                    vo.setJobTitle(full.getJobTitle());
                    vo.setCityName(full.getCityName());
                    vo.setCompanyName(full.getCompanyName());
                    vo.setCompanyLogo(full.getCompanyLogo());
                    vo.setSubmittedAt(full.getSubmittedAt());
                    vo.setUpdatedAt(full.getUpdatedAt());
                }
                return vo;
            }
            // 进行中（含 OFFER_ACCEPTED 已录用）→ 拒绝重复投递
            log.warn("投递失败: 重复投递, jobId={}, candidateId={}", dto.getJobId(), candidateId);
            throw new BusinessException(ResumeErrorCode.APPLICATION_ALREADY_EXISTS);
        }

        // ④ 计算匹配度（调用 lingxi-user 的匹配算法）
        BigDecimal matchScore = null;
        try {
            Map<String, Long> matchParams = new HashMap<>();
            matchParams.put("jobId", dto.getJobId());
            matchParams.put("userId", candidateId);
            Result<Integer> matchResult = matchFeignClient.calculateMatchScore(matchParams);
            if (matchResult != null && matchResult.getData() != null) {
                matchScore = new BigDecimal(matchResult.getData());
            }
        } catch (Exception e) {
            log.warn("计算匹配度失败，不影响投递: jobId={}, userId={}", dto.getJobId(), candidateId, e);
        }

        // ⑤ 入库 SUBMITTED（显式雪花ID）
        ResumeApplication application = new ResumeApplication();
        application.setId(SnowflakeIdUtil.nextId());
        application.setJobId(dto.getJobId());
        application.setCandidateId(candidateId);
        application.setResumeId(resume.getId());
        application.setStatus(ApplicationStatus.SUBMITTED.getCode());
        application.setMatchScore(matchScore);
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException e) {
            // uk_job_candidate 兜底（并发双投递）
            log.warn("投递失败: 唯一键冲突(并发重复投递), jobId={}, candidateId={}",
                    dto.getJobId(), candidateId);
            throw new BusinessException(ResumeErrorCode.APPLICATION_ALREADY_EXISTS);
        }

        // ⑤ 初始状态日志（fromStatus=null → SUBMITTED）
        writeStatusLog(application.getId(), null, ApplicationStatus.SUBMITTED.getCode(),
                candidateId, "CANDIDATE", "一键投递");

        // ⑥ 事务提交后发 MQ（消息先于数据被消费）
        Long applicationId = application.getId();
        registerAfterCommit(() -> eventProducer.sendNewApplication(applicationId, candidateId, dto.getJobId()));
        registerAfterCommit(() -> notifyHrNewApplication(applicationId));

        log.info("一键投递成功: applicationId={}, jobId={}, candidateId={}, resumeId={}",
                applicationId, dto.getJobId(), candidateId, resume.getId());

        // 返回投递结果（含岗位/企业信息）
        ApplicationVO vo = new ApplicationVO();
        vo.setId(applicationId);
        vo.setJobId(dto.getJobId());
        vo.setStatus(ApplicationStatus.SUBMITTED.getCode());
        ResumeApplication full = applicationMapper.selectByIdWithJob(applicationId);
        if (full != null) {
            vo.setJobTitle(full.getJobTitle());
            vo.setCityName(full.getCityName());
            vo.setCompanyName(full.getCompanyName());
            vo.setCompanyLogo(full.getCompanyLogo());
            vo.setSubmittedAt(full.getSubmittedAt());
            vo.setUpdatedAt(full.getUpdatedAt());
        }
        return vo;
    }

    // ==================== C端：列表/详情/撤回 ====================

    @Override
    public PageResult<ApplicationVO> listApplications(PageRequest pageRequest) {
        Long candidateId = getLoginUserId();

        ApplicationQuery query = new ApplicationQuery();
        query.setCandidateId(candidateId);

        PageHelper.startPage(pageRequest.getPage(), pageRequest.getPageSize());
        List<ResumeApplication> list = applicationMapper.selectByCondition(query);

        List<ApplicationVO> voList = list.stream().map(this::toApplicationVO).collect(Collectors.toList());
        PageInfo<ResumeApplication> pageInfo = new PageInfo<>(list);
        log.info("投递列表查询: candidateId={}, total={}", candidateId, pageInfo.getTotal());
        return PageResult.of(voList, pageInfo.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    public ApplicationDetailVO getApplicationDetail(Long id) {
        Long candidateId = getLoginUserId();
        ResumeApplication application = getOwnedApplication(id, candidateId);
        return toDetailVO(application);
    }

    @Override
    @Transactional
    public void withdraw(Long id) {
        Long candidateId = getLoginUserId();
        ResumeApplication application = getOwnedApplication(id, candidateId);

        ApplicationStatus current = ApplicationStatus.fromCode(application.getStatus());
        if (!current.isWithdrawable()) {
            log.warn("撤回失败: 当前状态不可撤回, applicationId={}, status={}", id, current.getCode());
            throw new BusinessException(ResumeErrorCode.STATUS_NOT_ALLOWED);
        }

        // 统一状态机入口（事务内）
        transition(id, current.getCode(), ApplicationStatus.WITHDRAWN.getCode(),
                candidateId, "CANDIDATE", "候选人撤回");

        registerAfterCommit(() -> eventProducer.sendApplicationWithdrawn(id, candidateId, application.getJobId()));
        log.info("撤回投递成功: applicationId={}, candidateId={}", id, candidateId);
    }

    @Override
    @Transactional
    public void acceptOffer(Long id) {
        Long candidateId = getLoginUserId();
        ResumeApplication application = getOwnedApplication(id, candidateId);

        // 状态校验：仅 OFFERED（待录用）可接受，其余状态经白名单拦截
        ApplicationStatus current = ApplicationStatus.fromCode(application.getStatus());
        if (current != ApplicationStatus.OFFERED) {
            log.warn("接受Offer失败: 当前状态不可接受, applicationId={}, status={}", id, current.getCode());
            throw new BusinessException(ResumeErrorCode.STATUS_NOT_ALLOWED);
        }

        // 统一状态机入口（事务内）
        transition(id, current.getCode(), ApplicationStatus.OFFER_ACCEPTED.getCode(),
                candidateId, "CANDIDATE", "候选人接受Offer");

        // 同步 lingxi-hr：hr_offer SENT→ACCEPTED + confirm HC（D 契约 2026-08-07）
        // 顺序约定：先本地 transition 后调 D；D 失败抛异常 → 回滚本地，重试时 D 幂等自愈
        Result<Void> offerResult = offerFeignClient.acceptOffer(new OfferAcceptRequest(id));
        if (offerResult == null || !offerResult.isSuccess()) {
            log.warn("接受Offer: 同步hr_offer失败, applicationId={}, code={}, message={}",
                    id, offerResult != null ? offerResult.getCode() : "N/A",
                    offerResult != null ? offerResult.getMessage() : "无响应");
            throw new BusinessException(
                    offerResult != null ? offerResult.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    offerResult != null ? offerResult.getMessage() : "Offer服务响应异常");
        }

        log.info("接受Offer成功: applicationId={}, candidateId={}", id, candidateId);
    }

    @Override
    @Transactional
    public void declineOffer(Long id, String rejectReason) {
        Long candidateId = getLoginUserId();
        ResumeApplication application = getOwnedApplication(id, candidateId);

        // 状态校验：仅 OFFERED（待录用）可拒绝
        ApplicationStatus current = ApplicationStatus.fromCode(application.getStatus());
        if (current != ApplicationStatus.OFFERED) {
            log.warn("拒绝Offer失败: 当前状态不可拒绝, applicationId={}, status={}", id, current.getCode());
            throw new BusinessException(ResumeErrorCode.STATUS_NOT_ALLOWED);
        }

        // 统一状态机入口（事务内）
        transition(id, current.getCode(), ApplicationStatus.OFFER_DECLINED.getCode(),
                candidateId, "CANDIDATE", "候选人拒绝Offer");

        // 同步 lingxi-hr：hr_offer SENT→REJECTED + rejected_at + reject_reason + release HC（D 契约 2026-08-07）
        Result<Void> offerResult = offerFeignClient.rejectOffer(new OfferRejectRequest(id, rejectReason));
        if (offerResult == null || !offerResult.isSuccess()) {
            log.warn("拒绝Offer: 同步hr_offer失败, applicationId={}, code={}, message={}",
                    id, offerResult != null ? offerResult.getCode() : "N/A",
                    offerResult != null ? offerResult.getMessage() : "无响应");
            throw new BusinessException(
                    offerResult != null ? offerResult.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    offerResult != null ? offerResult.getMessage() : "Offer服务响应异常");
        }

        log.info("拒绝Offer成功: applicationId={}, candidateId={}, rejectReason={}", id, candidateId, rejectReason);
    }

    // ==================== HR端（/internal，对齐 ResumeFeignClient 契约） ====================

    @Override
    public PageResult<InternalApplicationVO> listByCompany(Long companyId, List<Long> applicationIds, Long jobId, String status,
                                                           BigDecimal minMatchScore, String keyword, String sortBy,
                                                           Integer page, Integer pageSize) {
        ApplicationQuery query = new ApplicationQuery();
        query.setCompanyId(companyId);
        query.setApplicationIds(applicationIds);
        query.setJobId(jobId);
        query.setStatus(status);
        // sortBy 白名单防注入：不在白名单则忽略，退化为默认排序
        if (sortBy != null && ALLOWED_SORT_COLUMNS.contains(sortBy)) {
            query.setSortBy(sortBy);
        }
        if (minMatchScore != null) {
            query.setMinMatchScore(minMatchScore);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.setKeyword(keyword.trim());
        }

        // page/pageSize 同时传且有效时启用分页（HR Feign 契约带可选分页参数）
        if (page != null && page > 0 && pageSize != null && pageSize > 0) {
            PageHelper.startPage(page, pageSize);
        }
        List<ResumeApplication> list = applicationMapper.selectByCompanyId(query);
        List<InternalApplicationVO> voList = list.stream().map(this::toInternalVO).collect(Collectors.toList());
        PageInfo<ResumeApplication> pageInfo = new PageInfo<>(list);
        log.info("HR候选人列表查询: companyId={}, jobId={}, status={}, total={}, page={}, pageSize={}",
                companyId, jobId, status, pageInfo.getTotal(), page, pageSize);
        return PageResult.of(voList, pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public InternalApplicationVO getInternalById(Long id) {
        ResumeApplication application = applicationMapper.selectByIdWithJob(id);
        if (application == null) {
            throw new BusinessException(ResumeErrorCode.APPLICATION_NOT_FOUND);
        }
        return toInternalVO(application);
    }

    @Override
    @Transactional
    public void updateStatusByHr(Long id, String status, Long operatorId, String rejectFeedback) {
        ResumeApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(ResumeErrorCode.APPLICATION_NOT_FOUND);
        }
        // 统一状态机入口（HR 角色）
        transition(id, application.getStatus(), status, operatorId, "HR", null);
        // 落选反馈：REJECTED 时允许 HR 写入 reject_feedback（JSON）
        if (rejectFeedback != null && !rejectFeedback.isEmpty()) {
            ResumeApplication update = new ResumeApplication();
            update.setId(id);
            update.setRejectFeedback(rejectFeedback);
            applicationMapper.updateById(update);
        }
        log.info("HR更新投递状态: applicationId={}, from={}, to={}, operatorId={}, rejectFeedback={}",
                id, application.getStatus(), status, operatorId,
                rejectFeedback != null && !rejectFeedback.isEmpty());
    }

    @Override
    @Transactional
    public void handleInterviewEvaluated(Long applicationId, String result) {
        ResumeApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            log.warn("面试评估事件: 投递记录不存在, applicationId={}", applicationId);
            return;
        }
        String toStatus;
        String reason;
        if ("PASS".equalsIgnoreCase(result)) {
            toStatus = ApplicationStatus.OFFERABLE.getCode();
            reason = "面试通过";
        } else if ("FAIL".equalsIgnoreCase(result)) {
            toStatus = ApplicationStatus.REJECTED.getCode();
            reason = "面试未通过";
        } else {
            log.warn("面试评估事件: 未知评估结果, result={}, applicationId={}",
                    result, applicationId);
            return;
        }
        try {
            // 统一状态机入口（SYSTEM 角色，operatorId=0）
            transition(applicationId, application.getStatus(), toStatus, 0L, "SYSTEM", reason);
            log.info("面试评估事件处理成功: applicationId={}, from={}, to={}",
                    applicationId, application.getStatus(), toStatus);
        } catch (BusinessException e) {
            // 状态已流转/白名单不满足（消息乱序或重复）：仅告警，不抛异常（避免 MQ 无限重试）
            log.warn("面试评估事件状态流转失败: applicationId={}, result={}, error={}",
                    applicationId, result, e.getMessage());
        }
    }

    // ==================== 状态机核心 ====================

    /**
     * 统一状态流转入口：白名单校验 + 乐观锁 UPDATE + 状态日志（幂等）
     *
     * @param applicationId 投递记录ID
     * @param fromStatus    当前状态（乐观锁条件）
     * @param toStatus      目标状态
     * @param operatorId    操作人用户ID（SYSTEM 用 0L）
     * @param operatorRole  操作人角色：CANDIDATE/HR/SYSTEM
     * @param reason        变更原因（可空）
     * @return 本次变更日志
     */
    private ResumeStatusLog transition(Long applicationId, String fromStatus, String toStatus,
                                       Long operatorId, String operatorRole, String reason) {
        // ① 白名单校验
        Set<String> allowed = TRANSITION_MAP.get(fromStatus);
        if (allowed == null || !allowed.contains(toStatus)) {
            log.warn("状态流转被拒绝: applicationId={}, from={}, to={}", applicationId, fromStatus, toStatus);
            throw new BusinessException(ResumeErrorCode.STATUS_NOT_ALLOWED);
        }

        // ② 目标状态 -> 时间戳列名映射（防注入：列名必须来自白名单；无独立时间戳列的状态跳过写入，
        //    流转时间由状态日志 resume_status_log 记录）
        String targetColumn = STATUS_TIMESTAMP_COLUMN.get(toStatus);

        // ③ 乐观锁条件 UPDATE（rows=0 → 状态已被并发变更，天然幂等冲突）
        int rows = applicationMapper.updateStatus(applicationId, fromStatus, toStatus, targetColumn);
        if (rows == 0) {
            log.warn("状态冲突: applicationId={}, from={}, to={}", applicationId, fromStatus, toStatus);
            throw new BusinessException(ResumeErrorCode.STATUS_CONFLICT);
        }

        // ④ 状态日志（幂等键：确定性派生，重复调用不重复插入）
        return writeStatusLog(applicationId, fromStatus, toStatus, operatorId, operatorRole, reason);
    }

    /**
     * 写状态变更日志（幂等：同幂等键先查后插，DB uk_idempotent 兜底）
     */
    private ResumeStatusLog writeStatusLog(Long applicationId, String fromStatus, String toStatus,
                                           Long operatorId, String operatorRole, String reason) {
        String idempotentKey = buildIdempotentKey(applicationId, fromStatus, toStatus, operatorId);
        if (statusLogMapper.selectByIdempotentKey(idempotentKey) != null) {
            log.info("状态日志已存在，跳过: idempotentKey={}", idempotentKey);
            return null;
        }

        ResumeStatusLog logEntry = new ResumeStatusLog();
        logEntry.setApplicationId(applicationId);
        logEntry.setFromStatus(fromStatus);
        logEntry.setToStatus(toStatus);
        logEntry.setOperatorId(operatorId);
        logEntry.setOperatorRole(operatorRole);
        logEntry.setReason(reason);
        logEntry.setIdempotentKey(idempotentKey);
        try {
            statusLogMapper.insert(logEntry);
        } catch (DuplicateKeyException e) {
            // 并发下 uk_idempotent 冲突，视为已写入
            log.info("状态日志幂等冲突，忽略: idempotentKey={}", idempotentKey);
        }
        return logEntry;
    }

    /**
     * 幂等键：app:{applicationId}:{fromStatus}:{toStatus}:{operatorId}
     * 确定性派生，同一操作重复触发（含 MQ 重试）不产生重复日志
     */
    private String buildIdempotentKey(Long applicationId, String fromStatus, String toStatus, Long operatorId) {
        return "app:" + applicationId + ":" + fromStatus + ":" + toStatus + ":" + operatorId;
    }

    // ==================== 私有方法 ====================

    /**
     * 解析投递使用的简历：resumeId 为空 → 默认简历（无 → 3007）
     */
    private Resume resolveResume(Long candidateId, Long resumeId) {
        Resume resume;
        if (resumeId == null) {
            resume = resumeMapper.selectDefaultByCandidateId(candidateId);
            if (resume == null) {
                log.warn("投递失败: 无默认简历, candidateId={}", candidateId);
                throw new BusinessException(ResumeErrorCode.NO_DEFAULT);
            }
        } else {
            resume = resumeMapper.selectById(resumeId);
            if (resume == null || !candidateId.equals(resume.getCandidateId())) {
                log.warn("投递失败: 简历不存在或非本人, resumeId={}, candidateId={}", resumeId, candidateId);
                throw new BusinessException(ResumeErrorCode.RESUME_NOT_FOUND);
            }
            if (!"COMPLETED".equals(resume.getParseStatus())) {
                log.warn("投递失败: 简历未完成解析, resumeId={}, parseStatus={}", resumeId, resume.getParseStatus());
                throw new BusinessException(ResumeErrorCode.NOT_PARSED);
            }
        }
        return resume;
    }

    /**
     * 获取当前登录用户ID（未登录抛 401）
     */
    private Long getLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 查询本人投递记录（不存在或非本人均按不存在处理，防越权探测）
     *
     * <p>用带 JOIN 的 selectByIdWithJob，详情/撤回可直接使用岗位与企业信息。
     */
    private ResumeApplication getOwnedApplication(Long id, Long candidateId) {
        ResumeApplication application = applicationMapper.selectByIdWithJob(id);
        if (application == null || !candidateId.equals(application.getCandidateId())) {
            log.warn("投递记录不存在或无权访问: applicationId={}, candidateId={}", id, candidateId);
            throw new BusinessException(ResumeErrorCode.APPLICATION_NOT_FOUND);
        }
        return application;
    }

    /**
     * 事务提交后执行（MQ 发送等非事务操作）
     */
    private void registerAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    /**
     * 通知 HR 新投递（事务提交后执行，best-effort：失败仅告警，不影响投递闭环）
     */
    private void notifyHrNewApplication(Long applicationId) {
        try {
            Result<Void> result = hrNotifyFeignClient.notifyHrNewApplication(applicationId);
            if (result == null || !result.isSuccess()) {
                log.warn("通知HR新投递失败: applicationId={}, code={}, message={}",
                        applicationId, result != null ? result.getCode() : null,
                        result != null ? result.getMessage() : null);
            }
        } catch (Exception e) {
            log.warn("通知HR新投递异常（不阻塞）: applicationId={}, error={}", applicationId, e.getMessage());
        }
    }

    // ==================== VO 组装 ====================

    private ApplicationVO toApplicationVO(ResumeApplication application) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(application.getId());
        vo.setJobId(application.getJobId());
        vo.setJobTitle(application.getJobTitle());
        vo.setCityName(application.getCityName());
        vo.setCompanyName(application.getCompanyName());
        vo.setCompanyLogo(application.getCompanyLogo());
        vo.setStatus(application.getStatus());
        vo.setSubmittedAt(application.getSubmittedAt());
        vo.setUpdatedAt(application.getUpdatedAt());
        return vo;
    }

    private ApplicationDetailVO toDetailVO(ResumeApplication application) {
        ApplicationDetailVO vo = new ApplicationDetailVO();
        vo.setId(application.getId());
        vo.setJobId(application.getJobId());
        vo.setJobTitle(application.getJobTitle());
        vo.setCityName(application.getCityName());
        vo.setCompanyId(application.getCompanyId());
        vo.setCompanyName(application.getCompanyName());
        vo.setCompanyLogo(application.getCompanyLogo());
        vo.setResumeId(application.getResumeId());
        vo.setStatus(application.getStatus());
        vo.setMatchScore(application.getMatchScore());
        vo.setSubmittedAt(application.getSubmittedAt());
        vo.setViewedAt(application.getViewedAt());
        vo.setScreenedAt(application.getScreenedAt());
        vo.setInterviewingAt(application.getInterviewingAt());
        vo.setOfferableAt(application.getOfferableAt());
        vo.setOfferedAt(application.getOfferedAt());
        vo.setRejectedAt(application.getRejectedAt());
        vo.setWithdrawnAt(application.getWithdrawnAt());

        // 状态时间线（resume_status_log 正序）
        List<ApplicationTimelineVO> timeline = statusLogMapper.selectByApplicationId(application.getId())
                .stream()
                .map(this::toTimelineVO)
                .collect(Collectors.toList());
        vo.setTimeline(timeline);
        return vo;
    }

    private ApplicationTimelineVO toTimelineVO(ResumeStatusLog statusLog) {
        ApplicationTimelineVO vo = new ApplicationTimelineVO();
        vo.setFromStatus(statusLog.getFromStatus());
        vo.setToStatus(statusLog.getToStatus());
        vo.setOperatorRole(statusLog.getOperatorRole());
        vo.setReason(statusLog.getReason());
        vo.setCreatedAt(statusLog.getCreatedAt());
        return vo;
    }

    private InternalApplicationVO toInternalVO(ResumeApplication application) {
        InternalApplicationVO vo = new InternalApplicationVO();
        vo.setId(application.getId());
        vo.setCompanyId(application.getCompanyId());
        vo.setJobId(application.getJobId());
        vo.setJobTitle(application.getJobTitle());
        vo.setCandidateId(application.getCandidateId());
        vo.setResumeId(application.getResumeId());
        vo.setStatus(application.getStatus());
        vo.setMatchScore(application.getMatchScore());
        // aiScore 为 HR 侧预留字段，DB 无对应列
        vo.setAiScore(null);
        vo.setAppliedAt(application.getSubmittedAt());
        return vo;
    }

    // ==================== 管理后台统计（成员E lingxi-admin 看板） ====================

    @Override
    public Long getApplicationCount() {
        return applicationMapper.countAll();
    }

    @Override
    public ApplicationTrendVO getApplicationTrend(int days) {
        int safeDays = days <= 0 ? 7 : days;
        List<Map<String, Object>> rows = applicationMapper.countByDay(safeDays);

        // rows: [{dt: LocalDate, cnt: Long}, ...]，按日升序（无投递的日期不出现）
        List<String> dates = new java.util.ArrayList<>();
        List<Integer> counts = new java.util.ArrayList<>();
        String today = LocalDate.now().toString();
        int total = 0;
        int todayCount = 0;
        for (Map<String, Object> row : rows) {
            Object dt = row.get("dt");
            Object cnt = row.get("cnt");
            if (dt == null || cnt == null) {
                continue;
            }
            String dateStr = dt.toString();
            int c = ((Number) cnt).intValue();
            dates.add(dateStr);
            counts.add(c);
            total += c;
            if (today.equals(dateStr)) {
                todayCount = c;
            }
        }

        ApplicationTrendVO vo = new ApplicationTrendVO();
        vo.setDates(dates);
        vo.setCounts(counts);
        vo.setTotal(total);
        vo.setAverage(safeDays == 0 ? 0 : total / safeDays);
        vo.setTodayCount(todayCount);
        return vo;
    }

    // ==================== AI 分析更新 ====================

    @Override
    public void updateAiAnalysis(Long id, Map<String, Object> body) {
        ResumeApplication update = new ResumeApplication();
        update.setId(id);

        // 设置 aiScore
        Object aiScoreObj = body.get("aiScore");
        if (aiScoreObj != null) {
            update.setAiScore(new BigDecimal(aiScoreObj.toString()));
        }

        // 设置 aiAnalysis（JSON 字符串）
        Object aiAnalysisObj = body.get("aiAnalysis");
        if (aiAnalysisObj != null) {
            try {
                update.setAiAnalysis(objectMapper.writeValueAsString(aiAnalysisObj));
            } catch (Exception e) {
                log.warn("序列化 aiAnalysis 失败", e);
                update.setAiAnalysis(aiAnalysisObj.toString());
            }
        }

        applicationMapper.updateById(update);
        log.info("AI 分析结果已更新: applicationId={}, aiScore={}", id, update.getAiScore());
    }

    /**
     * JSON 字符串解析为对象（解析失败按原字符串返回，不影响主流程）
     */
    private Object parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }
}

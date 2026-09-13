package com.lingxi.hr.service.impl;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.enums.InterviewStatus;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.hr.domain.dto.EvaluationDTO;
import com.lingxi.hr.domain.dto.InterviewCreateDTO;
import com.lingxi.hr.domain.dto.RejectFeedbackDTO;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.domain.entity.HrInterview;
import com.lingxi.hr.domain.entity.HrInterviewEvaluation;
import com.lingxi.hr.domain.vo.EvaluationDetailVO;
import com.lingxi.hr.domain.vo.EvaluationResultVO;
import com.lingxi.hr.domain.vo.InterviewVO;
import com.lingxi.hr.domain.vo.PendingEvaluationVO;
import com.lingxi.hr.exception.HrErrorCode;
import com.lingxi.hr.feign.ResumeFeignClient;
import com.lingxi.hr.feign.UserFeignClient;
import com.lingxi.hr.feign.dto.ApplicationDTO;
import com.lingxi.hr.feign.dto.SysUserDTO;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.mapper.HrInterviewEvaluationMapper;
import com.lingxi.hr.mapper.HrInterviewMapper;
import com.lingxi.hr.service.HrInterviewService;
import com.lingxi.hr.service.ai.RejectFeedbackGenerator;
import com.lingxi.hr.service.notify.InterviewNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试协同服务实现（系分文档 5.5.3，Day 4-5）
 *
 * <p>状态机：PENDING → start → IN_PROGRESS → 评估提交(非草稿) → COMPLETED；任意非终态可取消。
 * 无独立「完成面试」接口，评估提交一次性置 COMPLETED（2026-08-06 确认）。
 *
 * <p>权限收紧（2026-08-06 确认）：start/cancel/评估/查看 —— HR_ADMIN 可操作本企业全部面试；
 * INTERVIEWER 仅可操作 interviewer_id == 当前用户的面试，否则 4011。
 *
 * <p>草稿覆盖：已有 is_draft=1 记录走 UPDATE 覆盖转正式，4104 仅对 is_draft=0 生效。
 * 待评估列表：IN_PROGRESS 且无正式评估（原 COMPLETED 恒空，2026-08-06 修正）。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrInterviewServiceImpl implements HrInterviewService {

    private static final String ROLE_HR_ADMIN = "HR_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String APP_STATUS_SCREENED = "SCREENED";
    private static final String APP_STATUS_INTERVIEWING = "INTERVIEWING";
    private static final String APP_STATUS_OFFERABLE = "OFFERABLE";
    private static final String APP_STATUS_REJECTED = "REJECTED";

    /** 时间冲突缓冲窗口（分钟） */
    private static final int CONFLICT_WINDOW_MINUTES = 60;

    private static final Map<String, String> METHOD_DESC = new HashMap<>();

    static {
        METHOD_DESC.put("OFFLINE", "线下面试");
        METHOD_DESC.put("ONLINE", "视频面试");
        METHOD_DESC.put("PHONE", "电话面试");
    }

    private final HrInterviewMapper hrInterviewMapper;
    private final HrInterviewEvaluationMapper hrInterviewEvaluationMapper;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final UserFeignClient userFeignClient;
    private final RejectFeedbackGenerator rejectFeedbackGenerator;
    private final InterviewNotifier interviewNotifier;

    // ==================== 创建面试 ====================

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InterviewVO createInterview(Long companyId, InterviewCreateDTO dto) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);

        // ① 投递详情（跨企业隔离校验）
        ApplicationDTO application = fetchApplication(dto.getApplicationId(), companyId);

        // ② 投递状态校验：仅 SCREENED 可安排面试（C 状态机 SCREENED→INTERVIEWING）
        if (!APP_STATUS_SCREENED.equals(application.getStatus())) {
            log.warn("创建面试失败: 投递非 SCREENED, applicationId={}, status={}",
                    dto.getApplicationId(), application.getStatus());
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }

        // ③ 面试官校验：本企业成员 + ACTIVE
        //    悲观锁 FOR UPDATE：串行化同一面试官的并发创建（同面试官并发在此排队，后到者等先到者提交）
        HrCompanyMember interviewer = hrCompanyMemberMapper.selectByCompanyAndUserForUpdate(companyId, dto.getInterviewerId());
        if (interviewer == null) {
            throw new BusinessException(HrErrorCode.MEMBER_NOT_FOUND);
        }
        if (!STATUS_ACTIVE.equals(interviewer.getStatus())) {
            throw new BusinessException(HrErrorCode.MEMBER_ACCOUNT_DISABLED);
        }

        // ④ 时间冲突校验（同面试官前后 60min 重叠）
        //    READ_COMMITTED：本事务每次 SELECT 读最新已提交数据（非 RR 固定快照）。
        //    配合 ③ 的行锁串行，后到者拿锁后 COUNT 必然能看到先到者已提交的面试 → 正确 4102。
        LocalDateTime scheduledAt = dto.getScheduledAt();
        if (hrInterviewMapper.countByInterviewerInTimeRange(
                dto.getInterviewerId(),
                scheduledAt.minusMinutes(CONFLICT_WINDOW_MINUTES),
                scheduledAt.plusMinutes(CONFLICT_WINDOW_MINUTES)) > 0) {
            throw new BusinessException(HrErrorCode.INTERVIEW_TIME_CONFLICT);
        }

        // ⑤ 入库（PENDING）
        HrInterview interview = new HrInterview();
        interview.setCompanyId(companyId);
        interview.setApplicationId(dto.getApplicationId());
        interview.setInterviewerId(dto.getInterviewerId());
        interview.setCandidateId(application.getCandidateId());
        interview.setJobId(application.getJobId());
        interview.setScheduledAt(scheduledAt);
        interview.setMethod(dto.getMethod());
        interview.setLocation(dto.getLocation());
        interview.setRemark(dto.getRemark());
        interview.setCandidateNote(dto.getCandidateNote());
        interview.setStatus(STATUS_PENDING);
        hrInterviewMapper.insert(interview);

        // ⑥ 投递状态联动 SCREENED→INTERVIEWING（失败抛异常回滚本地插入）
        updateApplicationStatus(dto.getApplicationId(), APP_STATUS_INTERVIEWING, null);

        // ⑦ 双端通知（best-effort，不阻塞）
        String jobTitle = application.getJobTitle();
        boolean candidateNotified = interviewNotifier.notifyCandidateInvite(
                application.getCandidateId(), interview.getId(), jobTitle, scheduledAt, dto.getLocation());
        boolean interviewerNotified = interviewNotifier.notifyInterviewerSchedule(
                dto.getInterviewerId(), interview.getId(), jobTitle, scheduledAt);
        log.info("创建面试: interviewId={}, applicationId={}, companyId={}, candidateNotified={}, interviewerNotified={}",
                interview.getId(), dto.getApplicationId(), companyId, candidateNotified, interviewerNotified);

        InterviewVO vo = new InterviewVO();
        vo.setInterviewId(interview.getId());
        vo.setApplicationId(dto.getApplicationId());
        vo.setCandidateId(application.getCandidateId());
        vo.setInterviewerId(dto.getInterviewerId());
        vo.setJobId(application.getJobId());
        vo.setJobTitle(jobTitle);
        vo.setScheduledAt(scheduledAt);
        vo.setMethod(dto.getMethod());
        vo.setMethodDesc(METHOD_DESC.getOrDefault(dto.getMethod(), dto.getMethod()));
        vo.setLocation(dto.getLocation());
        vo.setStatus(STATUS_PENDING);
        vo.setStatusDesc(InterviewStatus.PENDING.getDesc());
        return vo;
    }

    // ==================== 面试列表 ====================

    @Override
    public PageResult<InterviewVO> listInterviews(Long companyId, String status, String dateRange, Long jobId,
                                                  Long interviewerId, String method, String keyword,
                                                  Integer page, Integer size) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 权限：HR_ADMIN 全量；INTERVIEWER 仅可查本人（必须传 interviewerId 且等于当前用户，否则 4011）
        HrCompanyMember me = requireActiveMember(companyId);
        Long currentUserId = UserContext.getUserId();
        if (!ROLE_HR_ADMIN.equals(me.getRole())) {
            if (interviewerId == null || !interviewerId.equals(currentUserId)) {
                log.warn("面试官越权查询面试列表: userId={}, requestedInterviewerId={}", currentUserId, interviewerId);
                throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
            }
        }

        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        String validRange = normalizeDateRange(dateRange);

        long total = hrInterviewMapper.countPage(companyId, status, validRange, jobId, interviewerId, method, keyword);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<HrInterview> interviews = hrInterviewMapper.selectPage(
                companyId, status, validRange, jobId, interviewerId, method, keyword, (p - 1) * s, s);

        // 批量拼装用户信息（候选人 + 面试官）
        List<Long> userIds = new ArrayList<>();
        for (HrInterview i : interviews) {
            userIds.add(i.getCandidateId());
            userIds.add(i.getInterviewerId());
        }
        Map<Long, SysUserDTO> userMap = fetchUsers(userIds.stream().distinct().collect(Collectors.toList()));

        // 已评估集合（草稿也算 hasEvaluation=true）
        Set<Long> evaluatedInterviewIds = queryEvaluatedInterviewIds(interviews);

        List<InterviewVO> voList = new ArrayList<>(interviews.size());
        for (HrInterview i : interviews) {
            InterviewVO vo = new InterviewVO();
            vo.setInterviewId(i.getId());
            vo.setApplicationId(i.getApplicationId());
            vo.setCandidateId(i.getCandidateId());
            vo.setInterviewerId(i.getInterviewerId());
            vo.setJobId(i.getJobId());
            vo.setScheduledAt(i.getScheduledAt());
            vo.setMethod(i.getMethod());
            vo.setMethodDesc(METHOD_DESC.getOrDefault(i.getMethod(), i.getMethod()));
            vo.setLocation(i.getLocation());
            vo.setRemark(i.getRemark());
            vo.setCandidateNote(i.getCandidateNote());
            vo.setStatus(i.getStatus());
            vo.setStatusDesc(buildStatusDesc(i.getStatus()));

            SysUserDTO candidate = userMap.get(i.getCandidateId());
            vo.setCandidateName(candidate != null ? candidate.getName() : "用户" + i.getCandidateId());
            vo.setCandidateAvatar(candidate != null ? candidate.getAvatar() : null);
            SysUserDTO interviewer = userMap.get(i.getInterviewerId());
            vo.setInterviewerName(interviewer != null ? interviewer.getName() : "用户" + i.getInterviewerId());

            vo.setJobTitle(fetchJobTitle(i.getApplicationId(), i.getJobId()));
            vo.setHasQuestions(false);
            vo.setHasEvaluation(evaluatedInterviewIds.contains(i.getId()));
            voList.add(vo);
        }
        return PageResult.of(voList, total, p, s);
    }

    // ==================== 开始/取消面试 ====================

    @Override
    @Transactional
    public void startInterview(Long companyId, Long interviewId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);
        HrInterview interview = getOwnInterview(companyId, interviewId);

        String current = interview.getStatus();
        if (!STATUS_PENDING.equals(current) && !STATUS_SCHEDULED.equals(current)) {
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }
        if (hrInterviewMapper.updateStatus(interviewId, current, STATUS_IN_PROGRESS) == 0) {
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }
        log.info("开始面试: interviewId={}, companyId={}, from={}", interviewId, companyId, current);
    }

    @Override
    @Transactional
    public void cancelInterview(Long companyId, Long interviewId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);
        HrInterview interview = getOwnInterview(companyId, interviewId);

        String current = interview.getStatus();
        if (STATUS_COMPLETED.equals(current) || STATUS_CANCELLED.equals(current)) {
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }

        // ① 本地状态置 CANCELLED（乐观锁，rows=0 并发冲突）
        if (hrInterviewMapper.updateStatus(interviewId, current, STATUS_CANCELLED) == 0) {
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }

        // ② 投递回退 SCREENED（仅当投递仍为 INTERVIEWING；C 状态机 INTERVIEWING→SCREENED 已支持）
        ApplicationDTO application = fetchApplication(interview.getApplicationId(), companyId);
        if (APP_STATUS_INTERVIEWING.equals(application.getStatus())) {
            updateApplicationStatus(interview.getApplicationId(), APP_STATUS_SCREENED, null);
        }
        log.info("取消面试: interviewId={}, applicationId={}, companyId={}, from={}",
                interviewId, interview.getApplicationId(), companyId, current);
    }

    // ==================== 待评估列表 ====================

    @Override
    public List<PendingEvaluationVO> pendingEvaluations(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 权限：HR_ADMIN 全量；INTERVIEWER 仅本人待评估
        HrCompanyMember me = requireActiveMember(companyId);
        Long scopedInterviewerId = ROLE_HR_ADMIN.equals(me.getRole()) ? null : UserContext.getUserId();
        List<HrInterview> interviews = hrInterviewMapper.selectPendingEvaluations(companyId, scopedInterviewerId);
        if (interviews.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = interviews.stream().map(HrInterview::getCandidateId).distinct().collect(Collectors.toList());
        Map<Long, SysUserDTO> userMap = fetchUsers(userIds);

        LocalDateTime now = LocalDateTime.now();
        List<PendingEvaluationVO> list = new ArrayList<>(interviews.size());
        for (HrInterview i : interviews) {
            PendingEvaluationVO vo = new PendingEvaluationVO();
            vo.setInterviewId(i.getId());
            vo.setCandidateId(i.getCandidateId());
            SysUserDTO user = userMap.get(i.getCandidateId());
            vo.setCandidateName(user != null ? user.getName() : "用户" + i.getCandidateId());
            vo.setJobId(i.getJobId());
            vo.setJobTitle(fetchJobTitle(i.getApplicationId(), i.getJobId()));
            vo.setScheduledAt(i.getScheduledAt());
            vo.setIsOverdue(i.getScheduledAt() != null && i.getScheduledAt().plusHours(24).isBefore(now));
            list.add(vo);
        }
        return list;
    }

    // ==================== 查看评估 ====================

    @Override
    public EvaluationDetailVO getEvaluation(Long companyId, Long interviewId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);
        HrInterview interview = getOwnInterview(companyId, interviewId);

        HrInterviewEvaluation eval = hrInterviewEvaluationMapper.selectByInterviewId(interviewId);
        if (eval == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getErrorCode(), "该面试暂无评估记录");
        }

        EvaluationDetailVO vo = new EvaluationDetailVO();
        vo.setInterviewId(interviewId);
        vo.setConclusion(eval.getConclusion());
        vo.setTechScore(eval.getTechScore());
        vo.setCommunicationScore(eval.getCommunicationScore());
        vo.setMatchScore(eval.getMatchScore());
        vo.setPotentialScore(eval.getPotentialScore());
        vo.setComment(eval.getComment());
        vo.setFeedback(eval.getFeedback());
        vo.setIsDraft(eval.getIsDraft() != null && eval.getIsDraft() == 1);
        vo.setEvaluatorId(eval.getEvaluatorId());
        if (eval.getEvaluatorId() != null) {
            SysUserDTO evaluator = fetchUsers(Collections.singletonList(eval.getEvaluatorId())).get(eval.getEvaluatorId());
            vo.setEvaluatorName(evaluator != null ? evaluator.getName() : "用户" + eval.getEvaluatorId());
        }
        vo.setCreatedAt(eval.getCreatedAt());
        return vo;
    }

    // ==================== 录入评估 ====================

    @Override
    @Transactional
    public EvaluationResultVO submitEvaluation(Long companyId, Long interviewId, EvaluationDTO dto) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);
        HrInterview interview = getOwnInterview(companyId, interviewId);

        boolean formal = dto.getIsDraft() == null || !dto.getIsDraft();

        // ① 已评估校验：已有正式评估（is_draft=0）→ 4104；草稿（is_draft=1）放行覆盖
        HrInterviewEvaluation existing = hrInterviewEvaluationMapper.selectByInterviewId(interviewId);
        if (existing != null && existing.getIsDraft() != null && existing.getIsDraft() == 0) {
            throw new BusinessException(HrErrorCode.INTERVIEW_ALREADY_EVALUATED);
        }

        // ② 面试状态校验：仅 IN_PROGRESS 可评估
        if (!STATUS_IN_PROGRESS.equals(interview.getStatus())) {
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }

        // ③ 表单校验（正式提交全量；草稿仅要求结论 + 四维评分非空，满足 DB NOT NULL）
        validateEvaluation(dto, formal);

        // ④ 保存评估（无记录 INSERT，已有草稿 UPDATE 覆盖）
        Long evaluatorId = UserContext.getUserId();
        int isDraftInt = formal ? 0 : 1;
        Long evaluationId;
        if (existing == null) {
            HrInterviewEvaluation eval = new HrInterviewEvaluation();
            eval.setInterviewId(interviewId);
            eval.setConclusion(dto.getConclusion());
            eval.setTechScore(dto.getTechScore());
            eval.setCommunicationScore(dto.getCommunicationScore());
            eval.setMatchScore(dto.getMatchScore());
            eval.setPotentialScore(dto.getPotentialScore());
            eval.setComment(normalizeComment(dto.getComment()));
            eval.setIsDraft(isDraftInt);
            eval.setEvaluatorId(evaluatorId);
            hrInterviewEvaluationMapper.insert(eval);
            evaluationId = eval.getId();
        } else {
            existing.setConclusion(dto.getConclusion());
            existing.setTechScore(dto.getTechScore());
            existing.setCommunicationScore(dto.getCommunicationScore());
            existing.setMatchScore(dto.getMatchScore());
            existing.setPotentialScore(dto.getPotentialScore());
            existing.setComment(normalizeComment(dto.getComment()));
            existing.setIsDraft(isDraftInt);
            existing.setEvaluatorId(evaluatorId);
            hrInterviewEvaluationMapper.updateById(existing);
            evaluationId = existing.getId();
        }

        // ⑤ 草稿：仅保存，不触发流转
        if (!formal) {
            log.info("保存评估草稿: evaluationId={}, interviewId={}, companyId={}", evaluationId, interviewId, companyId);
            EvaluationResultVO draftVo = new EvaluationResultVO();
            draftVo.setEvaluationId(evaluationId);
            draftVo.setConclusion(dto.getConclusion());
            draftVo.setFeedback(null);
            draftVo.setApplicationStatus(null);
            draftVo.setNotificationSent(false);
            return draftVo;
        }

        // ⑥ 正式提交：面试 COMPLETED + 投递联动 + 通知
        if (hrInterviewMapper.updateStatus(interviewId, STATUS_IN_PROGRESS, STATUS_COMPLETED) == 0) {
            throw new BusinessException(HrErrorCode.INTERVIEW_STATUS_ERROR);
        }

        ApplicationDTO application = fetchApplication(interview.getApplicationId(), companyId);
        String applicationStatus;
        boolean notificationSent;
        String conclusion = dto.getConclusion();
        if ("PASS".equals(conclusion)) {
            updateApplicationStatus(interview.getApplicationId(), APP_STATUS_OFFERABLE, null);
            applicationStatus = APP_STATUS_OFFERABLE;
            notificationSent = interviewNotifier.notifyPass(
                    interview.getCandidateId(), interviewId, application.getJobTitle());
        } else if ("REJECT".equals(conclusion)) {
            RejectFeedbackDTO feedback = rejectFeedbackGenerator.generate(application);
            updateApplicationStatus(interview.getApplicationId(), APP_STATUS_REJECTED, JsonUtil.toJson(feedback));
            applicationStatus = APP_STATUS_REJECTED;
            notificationSent = interviewNotifier.notifyReject(
                    interview.getCandidateId(), interviewId, application.getJobTitle(), feedback);
        } else {
            // PENDING：投递保持 INTERVIEWING，通知 HR 安排复面
            applicationStatus = APP_STATUS_INTERVIEWING;
            notificationSent = notifyHrForFollowUp(companyId, interviewId,
                    application.getJobTitle(), application.getCandidateId());
        }

        log.info("正式提交面试评估: evaluationId={}, interviewId={}, conclusion={}, companyId={}, applicationStatus={}",
                evaluationId, interviewId, conclusion, companyId, applicationStatus);

        EvaluationResultVO vo = new EvaluationResultVO();
        vo.setEvaluationId(evaluationId);
        vo.setConclusion(conclusion);
        vo.setFeedback(null);
        vo.setApplicationStatus(applicationStatus);
        vo.setNotificationSent(notificationSent);
        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * 加载面试 + 企业隔离 + 操作权限（HR_ADMIN 或 interviewer==本人）
     */
    private HrInterview getOwnInterview(Long companyId, Long interviewId) {
        HrInterview interview = hrInterviewMapper.selectById(interviewId);
        if (interview == null || !companyId.equals(interview.getCompanyId())) {
            throw new BusinessException(HrErrorCode.INTERVIEW_NOT_FOUND);
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompanyMember me = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, userId);
        if (me == null || !STATUS_ACTIVE.equals(me.getStatus())) {
            throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
        }
        if (!ROLE_HR_ADMIN.equals(me.getRole())
                && !userId.equals(interview.getInterviewerId())) {
            log.warn("面试官越权操作: userId={}, interviewId={}, interviewerId={}",
                    userId, interviewId, interview.getInterviewerId());
            throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
        }
        return interview;
    }

    /**
     * 校验当前用户为本企业 ACTIVE 成员，否则抛 4011；返回成员记录供角色判断
     */
    private HrCompanyMember requireActiveMember(Long companyId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompanyMember me = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, userId);
        if (me == null || !STATUS_ACTIVE.equals(me.getStatus())) {
            throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
        }
        return me;
    }

    /**
     * 获取投递详情并做跨企业隔离校验（不存在/非本企业 → 4300/4301）
     */
    private ApplicationDTO fetchApplication(Long applicationId, Long companyId) {
        Result<ApplicationDTO> result;
        try {
            result = resumeFeignClient.getApplication(applicationId);
        } catch (Exception e) {
            log.error("获取投递详情异常: applicationId={}, error={}", applicationId, e.getMessage());
            throw new BusinessException(HrErrorCode.CANDIDATE_NOT_FOUND);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.warn("获取投递详情失败: applicationId={}, code={}, message={}",
                    applicationId, result != null ? result.getCode() : null,
                    result != null ? result.getMessage() : null);
            throw new BusinessException(HrErrorCode.CANDIDATE_NOT_FOUND);
        }
        ApplicationDTO application = result.getData();
        if (application.getCompanyId() == null || !application.getCompanyId().equals(companyId)) {
            log.warn("跨企业访问被拒绝: applicationId={}, companyId={}, appCompanyId={}",
                    applicationId, companyId, application.getCompanyId());
            throw new BusinessException(HrErrorCode.CANDIDATE_NO_PERMISSION);
        }
        return application;
    }

    /**
     * Feign 更新投递状态（失败透传 C 侧错误码/消息）
     */
    private void updateApplicationStatus(Long applicationId, String status, String rejectFeedback) {
        Result<Void> result;
        try {
            result = resumeFeignClient.updateApplicationStatus(applicationId, status, rejectFeedback);
        } catch (Exception e) {
            log.error("更新投递状态异常: applicationId={}, status={}, error={}",
                    applicationId, status, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "更新投递状态失败，请重试");
        }
        if (result == null || !result.isSuccess()) {
            log.warn("更新投递状态失败: applicationId={}, status={}, code={}, message={}",
                    applicationId, status, result != null ? result.getCode() : null,
                    result != null ? result.getMessage() : null);
            throw new BusinessException(result != null ? result.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    result != null ? result.getMessage() : "更新投递状态失败，请重试");
        }
    }

    /**
     * 批量查用户信息：优先 batch，失败降级循环单查，兜底返回空
     */
    private Map<Long, SysUserDTO> fetchUsers(List<Long> userIds) {
        Map<Long, SysUserDTO> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }
        try {
            String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            Result<List<SysUserDTO>> r = userFeignClient.batchUsers(ids);
            if (r != null && r.isSuccess() && r.getData() != null) {
                for (SysUserDTO u : r.getData()) {
                    if (u != null && u.getId() != null) {
                        map.put(u.getId(), u);
                    }
                }
                return map;
            }
            log.warn("批量查询用户失败: code={}, message={}",
                    r != null ? r.getCode() : null, r != null ? r.getMessage() : null);
        } catch (Exception e) {
            log.warn("批量查询用户异常，降级单查: {}", e.getMessage());
        }
        for (Long id : userIds) {
            try {
                Result<SysUserDTO> r = userFeignClient.getUserById(id);
                if (r != null && r.isSuccess() && r.getData() != null) {
                    map.put(id, r.getData());
                }
            } catch (Exception e) {
                log.warn("单查用户失败: id={}, {}", id, e.getMessage());
            }
        }
        return map;
    }

    /**
     * 批量查已评估面试ID集合（草稿也算已评估，前端用 hasEvaluation 区分查看/录入）
     */
    private Set<Long> queryEvaluatedInterviewIds(List<HrInterview> interviews) {
        if (interviews.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> interviewIds = interviews.stream().map(HrInterview::getId).collect(Collectors.toList());
        List<HrInterviewEvaluation> evals = hrInterviewEvaluationMapper.selectByInterviewIds(interviewIds);
        Set<Long> set = new HashSet<>();
        if (evals != null) {
            for (HrInterviewEvaluation e : evals) {
                if (e.getInterviewId() != null) {
                    set.add(e.getInterviewId());
                }
            }
        }
        return set;
    }

    /**
     * 取投递岗位标题（Feign 失败降级"岗位"+jobId，不阻塞列表）
     */
    private String fetchJobTitle(Long applicationId, Long jobId) {
        try {
            Result<ApplicationDTO> result = resumeFeignClient.getApplication(applicationId);
            if (result != null && result.isSuccess() && result.getData() != null
                    && result.getData().getJobTitle() != null) {
                return result.getData().getJobTitle();
            }
        } catch (Exception e) {
            log.warn("获取岗位标题失败: applicationId={}, error={}", applicationId, e.getMessage());
        }
        return jobId != null ? "岗位" + jobId : "";
    }

    /**
     * 待定结论通知 HR 安排复面（取本企业首位 HR_ADMIN）
     */
    private boolean notifyHrForFollowUp(Long companyId, Long interviewId, String jobTitle, Long candidateId) {
        List<HrCompanyMember> hrAdmins = hrCompanyMemberMapper.selectByCompanyId(companyId, ROLE_HR_ADMIN, null);
        if (hrAdmins == null || hrAdmins.isEmpty()) {
            log.warn("未找到企业 HR_ADMIN，跳过复面通知: companyId={}", companyId);
            return false;
        }
        String candidateName = "候选人" + candidateId;
        SysUserDTO candidate = fetchUsers(Collections.singletonList(candidateId)).get(candidateId);
        if (candidate != null && candidate.getName() != null) {
            candidateName = candidate.getName();
        }
        boolean notified = false;
        for (HrCompanyMember hrAdmin : hrAdmins) {
            notified |= interviewNotifier.notifyHrFollowUp(hrAdmin.getUserId(), interviewId, candidateName, jobTitle);
        }
        return notified;
    }

    /**
     * 评估表单校验：正式提交全量（结论枚举 + 四维 1-5 + 评语≥20字）；草稿仅要求结论 + 四维评分非空
     */
    private void validateEvaluation(EvaluationDTO dto, boolean formal) {
        String conclusion = dto.getConclusion();
        if (conclusion == null || conclusion.trim().isEmpty()) {
            throw new BusinessException(4103, "面试结论不能为空");
        }
        if (dto.getTechScore() == null || dto.getCommunicationScore() == null
                || dto.getMatchScore() == null || dto.getPotentialScore() == null) {
            throw new BusinessException(4103, "四项评分不能为空");
        }
        if (formal) {
            if (!"PASS".equals(conclusion) && !"PENDING".equals(conclusion) && !"REJECT".equals(conclusion)) {
                throw new BusinessException(4103, "面试结论不合法，仅支持 PASS/PENDING/REJECT");
            }
            if (outOfRange(dto.getTechScore()) || outOfRange(dto.getCommunicationScore())
                    || outOfRange(dto.getMatchScore()) || outOfRange(dto.getPotentialScore())) {
                throw new BusinessException(4103, "评分必须在1-5之间");
            }
            if (dto.getComment() == null || dto.getComment().trim().length() < 20) {
                throw new BusinessException(HrErrorCode.INTERVIEW_EVAL_COMMENT_TOO_SHORT);
            }
        }
    }

    private boolean outOfRange(Integer score) {
        return score == null || score < 1 || score > 5;
    }

    private String normalizeComment(String comment) {
        return comment == null ? "" : comment;
    }

    private String buildStatusDesc(String status) {
        try {
            return InterviewStatus.fromCode(status).getDesc();
        } catch (Exception e) {
            return status;
        }
    }

    private String normalizeDateRange(String dateRange) {
        if (dateRange == null) {
            return null;
        }
        String upper = dateRange.toUpperCase();
        if ("TODAY".equals(upper) || "WEEK".equals(upper) || "MONTH".equals(upper)) {
            return upper;
        }
        return null;
    }
}

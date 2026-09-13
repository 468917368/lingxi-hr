package com.lingxi.hr.service.impl;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.hr.domain.dto.MarkCandidateDTO;
import com.lingxi.hr.domain.dto.RejectFeedbackDTO;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.domain.entity.HrOffer;
import com.lingxi.hr.domain.vo.CandidateVO;
import com.lingxi.hr.domain.vo.MarkCandidateResultVO;
import com.lingxi.hr.domain.vo.TopCandidateVO;
import com.lingxi.hr.exception.HrErrorCode;
import com.lingxi.hr.feign.ResumeFeignClient;
import com.lingxi.hr.feign.UserFeignClient;
import com.lingxi.hr.feign.dto.ApplicationDTO;
import com.lingxi.hr.feign.dto.ResumeDetailDTO;
import com.lingxi.hr.feign.dto.SysUserDTO;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.mapper.HrInterviewMapper;
import com.lingxi.hr.mapper.HrOfferMapper;
import com.lingxi.hr.service.HrCandidateService;
import com.lingxi.hr.service.ai.RejectFeedbackGenerator;
import com.lingxi.hr.service.notify.CandidateNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 候选人管理服务实现
 *
 * <p>数据源：投递分页来自 lingxi-resume（Feign），姓名/电话/头像来自 lingxi-user（Feign）。
 * Feign 降级：用户信息 batch→单查→兜底；列表失败抛 500（前端依赖分页结构）；Top5 失败返回空。
 *
 * <p>依赖说明：标记合适(→SCREENED)/不合适(→REJECTED)依赖 C 侧状态机支持
 * SUBMITTED/VIEWED 直转，C 侧升级前新投递（SUBMITTED）标记会被 C 拒绝（3006）。
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrCandidateServiceImpl implements HrCandidateService {

    private static final String ROLE_HR_ADMIN = "HR_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 可标记状态：仅新投递/已查看 */
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_VIEWED = "VIEWED";
    private static final String STATUS_SCREENED = "SCREENED";
    private static final String STATUS_REJECTED = "REJECTED";

    /** lingxi-resume 简历不存在错误码（ResumeErrorCode.RESUME_NOT_FOUND），转 4303 */
    private static final int RESUME_NOT_FOUND_CODE = 3101;

    private final ResumeFeignClient resumeFeignClient;
    private final UserFeignClient userFeignClient;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final HrInterviewMapper hrInterviewMapper;
    private final HrOfferMapper hrOfferMapper;
    private final RejectFeedbackGenerator rejectFeedbackGenerator;
    private final CandidateNotifier candidateNotifier;

    // ==================== 候选人列表 ====================

    @Override
    public PageResult<CandidateVO> listCandidates(Long companyId, Long interviewerId, Long jobId, String status,
                                                  Integer minMatchScore, String keyword, String sortBy,
                                                  Integer page, Integer size) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);

        // 权限：HR_ADMIN 全量；INTERVIEWER 必须传本人 interviewerId，且列表范围限定本人负责的投递（否则 4011）
        HrCompanyMember me = requireActiveMember(companyId);
        Long currentUserId = UserContext.getUserId();
        List<Long> applicationIds = null;
        if (!ROLE_HR_ADMIN.equals(me.getRole())) {
            if (interviewerId == null || !interviewerId.equals(currentUserId)) {
                log.warn("面试官越权查询候选人列表: userId={}, requestedInterviewerId={}", currentUserId, interviewerId);
                throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
            }
            applicationIds = hrInterviewMapper.selectApplicationIdsByInterviewer(companyId, currentUserId);
            if (applicationIds == null || applicationIds.isEmpty()) {
                return PageResult.empty(p, s);
            }
        }

        BigDecimal minScore = minMatchScore == null ? BigDecimal.ZERO : BigDecimal.valueOf(minMatchScore);
        String sort = sortBy == null || sortBy.isEmpty() ? "matchScore" : sortBy;

        Result<PageResult<ApplicationDTO>> result;
        try {
            result = resumeFeignClient.getApplicationList(
                    companyId, applicationIds, status, jobId, minScore, keyword, sort, p, s);
        } catch (Exception e) {
            // 列表不能降级为空（前端依赖分页结构），失败抛 500
            log.error("候选人列表查询失败: companyId={}, error={}", companyId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "候选人列表查询失败，请稍后重试");
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("候选人列表查询失败: companyId={}, code={}, message={}",
                    companyId, result != null ? result.getCode() : null, result != null ? result.getMessage() : null);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "候选人列表查询失败，请稍后重试");
        }

        PageResult<ApplicationDTO> pageData = result.getData();
        List<Long> userIds = pageData.getList().stream()
                .map(ApplicationDTO::getCandidateId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUserDTO> userMap = fetchUsers(userIds);

        // 批量查已存在 Offer 记录的投递（含 WITHDRAWN/EXPIRED 等终态，前端据此禁用「发起 Offer」按钮，2026-08-07）
        Set<Long> offerRecordAppIds = queryOfferRecordAppIds(pageData.getList());
        // 批量查每投递最新 Offer 状态（前端 lastOfferStatus，2026-08-07）
        Map<Long, String> lastOfferStatusMap = queryLastOfferStatus(pageData.getList());

        List<CandidateVO> voList = new ArrayList<>(pageData.getList().size());
        for (ApplicationDTO app : pageData.getList()) {
            CandidateVO vo = new CandidateVO();
            vo.setId(app.getId());
            vo.setCandidateId(app.getCandidateId());
            vo.setJobId(app.getJobId());
            vo.setJobTitle(app.getJobTitle());
            vo.setStatus(app.getStatus());
            vo.setMatchScore(app.getMatchScore());
            vo.setAiScore(app.getAiScore());
            vo.setAppliedAt(app.getAppliedAt());
            vo.setHasOfferRecord(offerRecordAppIds.contains(app.getId()));
            vo.setLastOfferStatus(lastOfferStatusMap.get(app.getId()));

            SysUserDTO user = app.getCandidateId() != null ? userMap.get(app.getCandidateId()) : null;
            if (user != null) {
                vo.setCandidateName(user.getName());
                vo.setPhone(user.getPhone());
                vo.setAvatar(user.getAvatar());
            } else {
                vo.setCandidateName("用户" + app.getCandidateId());
                vo.setPhone("***");
            }
            voList.add(vo);
        }
        return PageResult.of(voList, pageData.getTotal(), p, s);
    }

    // ==================== Top5 高潜推荐 ====================

    @Override
    public List<TopCandidateVO> topCandidates(Long companyId, Long interviewerId, Long jobId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 权限：HR_ADMIN 全量；INTERVIEWER 必须传本人 interviewerId，范围限定本人负责的投递（否则 4011）
        HrCompanyMember me = requireActiveMember(companyId);
        Long currentUserId = UserContext.getUserId();
        List<Long> applicationIds = null;
        if (!ROLE_HR_ADMIN.equals(me.getRole())) {
            if (interviewerId == null || !interviewerId.equals(currentUserId)) {
                log.warn("面试官越权查询 Top5: userId={}, requestedInterviewerId={}", currentUserId, interviewerId);
                throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
            }
            applicationIds = hrInterviewMapper.selectApplicationIdsByInterviewer(companyId, currentUserId);
            if (applicationIds == null || applicationIds.isEmpty()) {
                return Collections.emptyList();
            }
        }

        Result<PageResult<ApplicationDTO>> result;
        try {
            // 排序契约：C 侧 matchScore DESC + aiScore DESC 取前 5
            result = resumeFeignClient.getApplicationList(
                    companyId, applicationIds, null, jobId, null, null, "matchScore", 1, 5);
        } catch (Exception e) {
            // Top5 为推荐性质，降级为空不阻塞
            log.warn("Top5 候选查询异常，降级为空: companyId={}, error={}", companyId, e.getMessage());
            return Collections.emptyList();
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.warn("Top5 候选查询失败，降级为空: companyId={}, code={}, message={}",
                    companyId, result != null ? result.getCode() : null, result != null ? result.getMessage() : null);
            return Collections.emptyList();
        }

        List<ApplicationDTO> apps = result.getData().getList();
        if (apps == null || apps.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = apps.stream()
                .map(ApplicationDTO::getCandidateId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUserDTO> userMap = fetchUsers(userIds);

        List<TopCandidateVO> list = new ArrayList<>(apps.size());
        int rank = 1;
        for (ApplicationDTO app : apps) {
            TopCandidateVO vo = new TopCandidateVO();
            vo.setRank(rank++);
            vo.setCandidateId(app.getCandidateId());
            vo.setApplicationId(app.getId());
            vo.setMatchScore(app.getMatchScore());
            vo.setAiScore(app.getAiScore());
            vo.setAdvantages(app.getAdvantages());
            vo.setRisks(app.getRisks());
            SysUserDTO user = app.getCandidateId() != null ? userMap.get(app.getCandidateId()) : null;
            vo.setCandidateName(user != null ? user.getName() : "用户" + app.getCandidateId());
            list.add(vo);
        }
        return list;
    }

    // ==================== 标记合适/不合适 ====================

    @Override
    public MarkCandidateResultVO markCandidate(Long companyId, Long applicationId, MarkCandidateDTO dto) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 标记为筛选决策，仅 HR_ADMIN
        requireHrAdmin(companyId);

        // ① 获取投递详情（归属校验 + 状态机校验 + 通知拼装）
        ApplicationDTO application = fetchApplication(applicationId, companyId);

        // ② 状态机校验：已处理/已进入后续流程不可再标记
        validateMarkable(application.getStatus());

        String newStatus;
        String newStatusDesc;
        boolean notificationSent;
        if ("SUITABLE".equals(dto.getAction())) {
            // 合适 → SCREENED
            newStatus = STATUS_SCREENED;
            newStatusDesc = "筛选通过";
            updateStatus(applicationId, newStatus, null);
            notificationSent = candidateNotifier.notifyScreenedPass(
                    application.getCandidateId(), applicationId, application.getJobTitle());
        } else {
            // 不合适 → REJECTED + AI 落选反馈 + 通知
            newStatus = STATUS_REJECTED;
            newStatusDesc = "已淘汰";
            RejectFeedbackDTO feedback = rejectFeedbackGenerator.generate(application);
            updateStatus(applicationId, newStatus, JsonUtil.toJson(feedback));
            notificationSent = candidateNotifier.notifyRejected(
                    application.getCandidateId(), applicationId, application.getJobTitle(), feedback);
        }

        log.info("标记候选人: applicationId={}, action={}, from={}, to={}, companyId={}, notificationSent={}",
                applicationId, dto.getAction(), application.getStatus(), newStatus, companyId, notificationSent);

        MarkCandidateResultVO vo = new MarkCandidateResultVO();
        vo.setApplicationId(applicationId);
        vo.setNewStatus(newStatus);
        vo.setNewStatusDesc(newStatusDesc);
        vo.setNotificationSent(notificationSent);
        return vo;
    }

    // ==================== 查看候选人简历 ====================

    @Override
    public ResumeDetailDTO getCandidateResume(Long companyId, Long applicationId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);

        // ① 投递详情校验（复用：不存在/查询失败 → 4300；跨企业 → 4301）
        ApplicationDTO application = fetchApplication(applicationId, companyId);

        // ② 候选人未上传简历（投递存在但无 resumeId）
        Long resumeId = application.getResumeId();
        if (resumeId == null) {
            log.warn("候选人简历不存在: applicationId={}, companyId={}", applicationId, companyId);
            throw new BusinessException(HrErrorCode.CANDIDATE_RESUME_NOT_FOUND);
        }

        // ③ Feign 调 C 内部简历详情
        Result<ResumeDetailDTO> result;
        try {
            result = resumeFeignClient.getInternalResume(resumeId);
        } catch (Exception e) {
            log.error("获取简历详情异常: applicationId={}, resumeId={}, error={}",
                    applicationId, resumeId, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "简历获取失败，请稍后重试");
        }
        if (result == null || !result.isSuccess()) {
            log.warn("获取简历详情失败: applicationId={}, resumeId={}, code={}, message={}",
                    applicationId, resumeId, result != null ? result.getCode() : null,
                    result != null ? result.getMessage() : null);
            // C 返回 RESUME_NOT_FOUND(3101) → 4303；其他错误码透传
            if (result.getCode() == RESUME_NOT_FOUND_CODE) {
                throw new BusinessException(HrErrorCode.CANDIDATE_RESUME_NOT_FOUND);
            }
            throw new BusinessException(result != null ? result.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    result != null ? result.getMessage() : "简历获取失败，请稍后重试");
        }
        if (result.getData() == null) {
            log.warn("获取简历详情成功但数据为空: applicationId={}, resumeId={}", applicationId, resumeId);
            throw new BusinessException(HrErrorCode.CANDIDATE_RESUME_NOT_FOUND);
        }

        log.info("查看候选人简历: companyId={}, applicationId={}, resumeId={}", companyId, applicationId, resumeId);

        // ④ 简历查看成功 → 投递状态 SUBMITTED → VIEWED + 通知候选人（best-effort，不阻塞简历展示）
        if (STATUS_SUBMITTED.equals(application.getStatus())) {
            try {
                updateStatus(applicationId, STATUS_VIEWED, null);
                candidateNotifier.notifyResumeViewed(
                        application.getCandidateId(), applicationId, application.getJobTitle());
                log.info("查看简历触发投递状态 VIEWED: applicationId={}, companyId={}", applicationId, companyId);
            } catch (Exception e) {
                log.warn("查看简历更新投递状态失败（不阻塞简历展示）: applicationId={}, error={}",
                        applicationId, e.getMessage());
            }
        }

        ResumeDetailDTO resume = result.getData();

        // ⑤ 检查盲选模式
        Long candidateId = application.getCandidateId();
        boolean blindMode = isBlindMode(candidateId);
        resume.setBlindMode(blindMode);
        if (blindMode) {
            log.info("候选人开启盲选模式，过滤敏感信息: candidateId={}", candidateId);
            // 隐藏个人信息
            resume.setCandidateName("求职者");
            resume.setPhone(null);
            resume.setEmail(null);
            resume.setWechat(null);
            resume.setFacePhotoUrl(null);
            // 过滤敏感章节
            resume.setCardStructure(filterSensitiveSections(resume.getCardStructure()));
        }

        log.info("查看候选人简历: companyId={}, applicationId={}, blindMode={}", companyId, applicationId, blindMode);
        return resume;
    }

    @Override
    public ResumeDetailDTO getCandidateResumeByUserId(Long companyId, Long userId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireActiveMember(companyId);

        // 检查候选人是否与该公司有关联（投递过简历或被推荐过）
        boolean hasRelation = checkCandidateRelation(companyId, userId);
        if (!hasRelation) {
            log.warn("候选人与公司无关联，拒绝访问: companyId={}, userId={}", companyId, userId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Feign 调用简历服务，根据用户ID获取简历
        Result<ResumeDetailDTO> result;
        try {
            result = resumeFeignClient.getResumeByUserId(userId);
        } catch (Exception e) {
            log.error("获取候选人简历异常: userId={}, error={}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "简历获取失败，请稍后重试");
        }
        if (result == null || !result.isSuccess()) {
            log.warn("获取候选人简历失败: userId={}, code={}, message={}", userId,
                    result != null ? result.getCode() : null,
                    result != null ? result.getMessage() : null);
            throw new BusinessException(HrErrorCode.CANDIDATE_RESUME_NOT_FOUND);
        }
        if (result.getData() == null) {
            log.warn("获取候选人简历成功但数据为空: userId={}", userId);
            throw new BusinessException(HrErrorCode.CANDIDATE_RESUME_NOT_FOUND);
        }

        ResumeDetailDTO resume = result.getData();

        // 检查盲选模式
        boolean blindMode = isBlindMode(userId);
        resume.setBlindMode(blindMode);
        if (blindMode) {
            log.info("候选人开启盲选模式，过滤敏感信息: userId={}", userId);
            // 隐藏个人信息
            resume.setCandidateName("求职者");
            resume.setPhone(null);
            resume.setEmail(null);
            resume.setWechat(null);
            resume.setFacePhotoUrl(null);
            // 过滤敏感章节
            resume.setCardStructure(filterSensitiveSections(resume.getCardStructure()));
        }

        log.info("查看候选人简历: companyId={}, userId={}, blindMode={}", companyId, userId, blindMode);
        return resume;
    }

    /**
     * 检查用户是否开启盲选模式
     */
    private boolean isBlindMode(Long userId) {
        try {
            Result<Map<String, Object>> privacyResult = userFeignClient.getUserPrivacy(userId);
            if (privacyResult != null && privacyResult.isSuccess() && privacyResult.getData() != null) {
                Object blindMode = privacyResult.getData().get("blindMode");
                return Boolean.TRUE.equals(blindMode);
            }
        } catch (Exception e) {
            log.warn("获取用户隐私设置失败: userId={}", userId, e);
        }
        return false;
    }

    /**
     * 检查候选人是否与该公司有关联
     * 关联条件：投递过该公司的岗位
     */
    private boolean checkCandidateRelation(Long companyId, Long userId) {
        try {
            // 查询候选人是否投递过该公司的岗位
            Result<PageResult<ApplicationDTO>> result = resumeFeignClient.getApplicationList(
                    companyId, null, null, null, null, null, null, 1, 1);
            if (result != null && result.isSuccess() && result.getData() != null) {
                // 如果有投递记录，说明有关联
                return result.getData().getTotal() > 0;
            }
        } catch (Exception e) {
            log.warn("检查候选人关联关系失败: companyId={}, userId={}", companyId, userId, e);
        }
        return false;
    }

    /**
     * 过滤敏感章节（盲选模式）
     * 过滤：基本信息、教育背景
     * 保留：工作经验、技能、项目经历
     */
    @SuppressWarnings("unchecked")
    private Object filterSensitiveSections(Object cardStructure) {
        if (cardStructure == null) return null;

        try {
            Map<String, Object> card;
            if (cardStructure instanceof Map) {
                card = (Map<String, Object>) cardStructure;
            } else {
                return cardStructure;
            }

            Object sections = card.get("sections");
            if (sections instanceof List) {
                List<Map<String, Object>> filteredSections = new ArrayList<>();
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        Map<String, Object> sectionMap = (Map<String, Object>) section;
                        String title = (String) sectionMap.get("title");
                        // 过滤敏感章节：基本信息、教育背景
                        if (title != null && (
                                title.contains("基本信息") || title.contains("个人信息")
                                || title.contains("基本资料") || title.contains("个人资料")
                                || title.contains("联系方式")
                                || title.contains("教育") || title.contains("学历")
                                || title.toLowerCase().contains("basic info")
                                || title.toLowerCase().contains("personal info")
                                || title.toLowerCase().contains("education")
                        )) {
                            log.debug("盲选模式过滤章节: {}", title);
                            continue; // 跳过敏感章节
                        }
                        filteredSections.add(sectionMap);
                    }
                }
                card.put("sections", filteredSections);
            }
            return card;
        } catch (Exception e) {
            log.warn("过滤敏感章节失败", e);
            return cardStructure;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 批量查已存在 Offer 记录的投递ID集合（候选人列表 hasOfferRecord，2026-08-07）
     */
    private Set<Long> queryOfferRecordAppIds(List<ApplicationDTO> apps) {
        List<Long> appIds = apps.stream().map(ApplicationDTO::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (appIds.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<Long> offerIds = hrOfferMapper.selectOfferRecordAppIds(appIds);
            return offerIds == null ? Collections.emptySet() : new HashSet<>(offerIds);
        } catch (Exception e) {
            log.warn("查询投递Offer记录失败，hasOfferRecord 默认 false: error={}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 批量查每投递最新一条 Offer 状态（候选人列表 lastOfferStatus，2026-08-07 前端清单）
     */
    private Map<Long, String> queryLastOfferStatus(List<ApplicationDTO> apps) {
        List<Long> appIds = apps.stream().map(ApplicationDTO::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (appIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<HrOffer> offers = hrOfferMapper.selectLastOfferStatus(appIds);
            Map<Long, String> map = new HashMap<>();
            if (offers != null) {
                for (HrOffer offer : offers) {
                    if (offer.getApplicationId() != null) {
                        map.put(offer.getApplicationId(), offer.getStatus());
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("查询投递最新Offer状态失败，lastOfferStatus 默认 null: error={}", e.getMessage());
            return Collections.emptyMap();
        }
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
            log.warn("跨企业候选人标记被拒绝: applicationId={}, companyId={}, appCompanyId={}",
                    applicationId, companyId, application.getCompanyId());
            throw new BusinessException(HrErrorCode.CANDIDATE_NO_PERMISSION);
        }
        return application;
    }

    /**
     * 标记状态机校验：仅 SUBMITTED/VIEWED 可标记，否则 4302
     */
    private void validateMarkable(String currentStatus) {
        if (STATUS_SCREENED.equals(currentStatus) || STATUS_REJECTED.equals(currentStatus)) {
            throw new BusinessException(HrErrorCode.CANDIDATE_ALREADY_PROCESSED);
        }
        if (!STATUS_SUBMITTED.equals(currentStatus) && !STATUS_VIEWED.equals(currentStatus)) {
            throw new BusinessException(HrErrorCode.CANDIDATE_ALREADY_PROCESSED);
        }
    }

    /**
     * Feign 更新投递状态（失败透传 C 侧错误码/消息）
     */
    private void updateStatus(Long applicationId, String status, String rejectFeedback) {
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
     * 校验当前用户为本企业 HR_ADMIN，否则抛 4011
     */
    private void requireHrAdmin(Long companyId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompanyMember me = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, userId);
        if (me == null || !ROLE_HR_ADMIN.equals(me.getRole())) {
            throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
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
        // 降级：循环单查
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
}

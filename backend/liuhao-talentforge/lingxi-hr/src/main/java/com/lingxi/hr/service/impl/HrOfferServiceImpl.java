package com.lingxi.hr.service.impl;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.enums.OfferStatus;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.SnowflakeIdUtil;
import com.lingxi.hr.domain.dto.OfferCreateDTO;
import com.lingxi.hr.domain.entity.HrCompany;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.domain.entity.HrOffer;
import com.lingxi.hr.domain.vo.HcOverviewVO;
import com.lingxi.hr.domain.vo.OfferActionResultVO;
import com.lingxi.hr.domain.vo.OfferCreateResultVO;
import com.lingxi.hr.domain.vo.OfferDetailVO;
import com.lingxi.hr.domain.vo.OfferVO;
import com.lingxi.hr.exception.HrErrorCode;
import com.lingxi.hr.feign.JobFeignClient;
import com.lingxi.hr.feign.ResumeFeignClient;
import com.lingxi.hr.feign.UserFeignClient;
import com.lingxi.hr.feign.dto.ApplicationDTO;
import com.lingxi.hr.feign.dto.CompanyJobDTO;
import com.lingxi.hr.feign.dto.HcConfirmRequest;
import com.lingxi.hr.feign.dto.HcReleaseRequest;
import com.lingxi.hr.feign.dto.HcReserveRequest;
import com.lingxi.hr.feign.dto.JobHcOverviewDTO;
import com.lingxi.hr.feign.dto.SysUserDTO;
import com.lingxi.hr.mapper.HrCompanyMapper;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.mapper.HrOfferMapper;
import com.lingxi.hr.service.HrOfferService;
import com.lingxi.hr.service.notify.OfferNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
 * Offer 管理服务实现（系分 5.5.4，Day 8）
 *
 * <p>状态机：SENT → ACCEPTED/REJECTED/EXPIRED/WITHDRAWN（复用 common OfferStatus）。
 * 投递联动：接受 OFFERABLE→OFFERED；拒绝 OFFERABLE→REJECTED（C 落地 WITHDRAWN 后切回）。
 * 撤回不联动投递；uk_application_id 唯一约束 → 撤回/过期后不可再次发起。
 *
 * <p>权限：5 个 HR 接口仅 HR_ADMIN；详情/接受/拒绝为候选人接口（userId==candidateId）。
 * HC 操作均对齐 B 契约（reserve/confirm/release），release 幂等。
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrOfferServiceImpl implements HrOfferService {

    private static final String ROLE_HR_ADMIN = "HR_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String OFFER_STATUS_SENT = "SENT";
    private static final String OFFER_STATUS_ACCEPTED = "ACCEPTED";
    private static final String OFFER_STATUS_REJECTED = "REJECTED";
    private static final String OFFER_STATUS_EXPIRED = "EXPIRED";
    private static final String OFFER_STATUS_WITHDRAWN = "WITHDRAWN";

    private static final String APP_STATUS_OFFERABLE = "OFFERABLE";
    private static final String APP_STATUS_OFFERED = "OFFERED";
    private static final String APP_STATUS_OFFER_ACCEPTED = "OFFER_ACCEPTED";
    private static final String APP_STATUS_OFFER_DECLINED = "OFFER_DECLINED";

    /** 默认 Offer 有效期天数（用户确认 2026-08-07，系分原 7） */
    private static final int DEFAULT_EXPIRES_DAYS = 3;
    private static final int MAX_EXPIRES_DAYS = 30;

    /** B 侧错误码：2201 无可用HC */
    private static final int B_HC_NOT_AVAILABLE = 2201;

    /** B 释放原因（白名单 REJECTED/EXPIRED/NOT_ONBOARDED/D_PERSIST_FAILED） */
    private static final String RELEASE_REASON_REJECTED = "REJECTED";
    private static final String RELEASE_REASON_EXPIRED = "EXPIRED";
    private static final String RELEASE_REASON_PERSIST_FAILED = "D_PERSIST_FAILED";

    private final HrOfferMapper hrOfferMapper;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final HrCompanyMapper hrCompanyMapper;
    private final JobFeignClient jobFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final UserFeignClient userFeignClient;
    private final OfferNotifier offerNotifier;

    // ==================== 发起 Offer ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfferCreateResultVO createOffer(Long companyId, OfferCreateDTO dto) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);

        // ① 投递详情 + 跨企业隔离 + 状态校验
        ApplicationDTO application = fetchApplication(dto.getApplicationId(), companyId);
        if (!APP_STATUS_OFFERABLE.equals(application.getStatus())) {
            log.warn("发起Offer失败: 投递非OFFERABLE, applicationId={}, status={}",
                    dto.getApplicationId(), application.getStatus());
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }

        // ② 参数校验：入职未来 / 有效期默认3天且1~30
        LocalDate today = LocalDate.now();
        if (dto.getEntryDate() == null || !dto.getEntryDate().isAfter(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getErrorCode(), "入职时间必须在未来日期");
        }
        int expiresDays = dto.getExpiresInDays() == null ? DEFAULT_EXPIRES_DAYS : dto.getExpiresInDays();
        if (expiresDays < 1 || expiresDays > MAX_EXPIRES_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getErrorCode(), "有效期天数必须在1-30之间");
        }
        if (dto.getSalary() == null || dto.getSalary() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getErrorCode(), "薪资必须大于0");
        }

        // ③ 重复发起校验（2026-08-07 前端清单：已删 uk_application_id 唯一约束）
        //    仅存在活跃 SENT / 终态 ACCEPTED·REJECTED → 4205；WITHDRAWN/EXPIRED 旧记录放行可重发
        HrOffer existing = hrOfferMapper.selectByApplicationId(dto.getApplicationId());
        if (existing != null) {
            String existingStatus = existing.getStatus();
            if (OFFER_STATUS_SENT.equals(existingStatus)
                    || OFFER_STATUS_ACCEPTED.equals(existingStatus)
                    || OFFER_STATUS_REJECTED.equals(existingStatus)) {
                throw new BusinessException(HrErrorCode.OFFER_ALREADY_EXISTS);
            }
        }

        // ④ 生成 Snowflake id
        Long offerId = SnowflakeIdUtil.nextId();

        // ⑤ 薪资软提示（best-effort：B 查询失败不阻塞，reserve 会兜底岗位校验）
        JobHcOverviewDTO job = queryJobBestEffort(application.getJobId());
        OfferCreateResultVO.SalaryWarning warning = computeSalaryWarning(dto, job);

        // ⑥ reserve HC（事务内，失败抛异常整体回滚本地；成功但后续失败补偿 release）
        try {
            reserveHc(application.getJobId(), companyId, offerId, application.getCandidateId());

            // ⑦ 插入 Offer
            HrOffer offer = new HrOffer();
            offer.setId(offerId);
            offer.setCompanyId(companyId);
            offer.setApplicationId(dto.getApplicationId());
            offer.setCandidateId(application.getCandidateId());
            offer.setJobId(application.getJobId());
            offer.setSalary(dto.getSalary());
            offer.setEntryDate(dto.getEntryDate());
            offer.setLevel(dto.getLevel());
            offer.setRemark(dto.getRemark());
            offer.setStatus(OFFER_STATUS_SENT);
            offer.setExpiresAt(LocalDateTime.now().plusDays(expiresDays));
            offer.setUrgeCount(0);
            hrOfferMapper.insert(offer);

            // 投递联动：OFFERABLE → OFFERED（待录用，C 新状态机，2026-08-07）
            updateApplicationStatus(dto.getApplicationId(), APP_STATUS_OFFERED, null);
        } catch (BusinessException e) {
            if (e.getCode() == HrErrorCode.OFFER_HC_INSUFFICIENT.getErrorCode()) {
                throw e;
            }
            releaseBestEffort(application.getJobId(), companyId, offerId, RELEASE_REASON_PERSIST_FAILED);
            throw e;
        } catch (Exception e) {
            log.error("发起Offer失败: applicationId={}, offerId={}, error={}",
                    dto.getApplicationId(), offerId, e.getMessage());
            releaseBestEffort(application.getJobId(), companyId, offerId, RELEASE_REASON_PERSIST_FAILED);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "发起Offer失败，请稍后重试");
        }

        // ⑧ 通知候选人（best-effort）
        boolean notified = offerNotifier.notifyOfferReceived(
                application.getCandidateId(), offerId, application.getJobTitle(),
                dto.getSalary(), dto.getEntryDate(), LocalDateTime.now().plusDays(expiresDays));
        log.info("发起Offer: offerId={}, applicationId={}, companyId={}, notified={}",
                offerId, dto.getApplicationId(), companyId, notified);

        OfferCreateResultVO vo = new OfferCreateResultVO();
        vo.setOfferId(offerId);
        vo.setStatus(OFFER_STATUS_SENT);
        vo.setExpiresAt(LocalDateTime.now().plusDays(expiresDays));
        vo.setNotificationSent(notified);
        vo.setSalaryWarning(warning);
        return vo;
    }

    // ==================== Offer 列表 ====================

    @Override
    public PageResult<OfferVO> listOffers(Long companyId, String status, String dateRange, Long jobId,
                                          Integer page, Integer size) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);

        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        String validRange = normalizeDateRange(dateRange);

        long total = hrOfferMapper.countPage(companyId, status, validRange, jobId);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<HrOffer> offers = hrOfferMapper.selectPage(companyId, status, validRange, jobId, (p - 1) * s, s);

        // 候选人姓名（batch→单查→兜底）
        List<Long> candidateIds = offers.stream().map(HrOffer::getCandidateId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, SysUserDTO> userMap = fetchUsers(candidateIds);

        // 岗位标题（按 jobId 去重，B getJobForValidation 查询，失败降级"岗位"+id）
        Map<Long, String> jobTitleMap = fetchJobTitles(offers);

        List<OfferVO> voList = new ArrayList<>(offers.size());
        for (HrOffer offer : offers) {
            OfferVO vo = new OfferVO();
            vo.setOfferId(offer.getId());
            vo.setApplicationId(offer.getApplicationId());
            vo.setCandidateId(offer.getCandidateId());
            vo.setJobId(offer.getJobId());
            vo.setJobTitle(jobTitleMap.getOrDefault(offer.getJobId(), "岗位" + offer.getJobId()));
            vo.setSalary(offer.getSalary());
            vo.setEntryDate(offer.getEntryDate());
            vo.setLevel(offer.getLevel());
            vo.setStatus(offer.getStatus());
            vo.setStatusDesc(buildStatusDesc(offer.getStatus()));
            vo.setExpiresAt(offer.getExpiresAt());
            vo.setUrgeCount(offer.getUrgeCount());
            vo.setRejectReason(offer.getRejectReason());
            vo.setAcceptedAt(offer.getAcceptedAt());
            vo.setRejectedAt(offer.getRejectedAt());
            vo.setCreatedAt(offer.getCreatedAt());

            SysUserDTO user = offer.getCandidateId() != null ? userMap.get(offer.getCandidateId()) : null;
            vo.setCandidateName(user != null ? user.getName() : "用户" + offer.getCandidateId());
            voList.add(vo);
        }
        return PageResult.of(voList, total, p, s);
    }

    // ==================== HC 概览 ====================

    @Override
    public HcOverviewVO getHcOverview(Long companyId, Long jobId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);

        HcOverviewVO vo = new HcOverviewVO();
        if (jobId != null) {
            // 单岗
            JobHcOverviewDTO job = queryJobOrThrow(jobId);
            vo.setJobId(job.getJobId());
            vo.setJobTitle(job.getTitle());
            vo.setTotalHc(nvl(job.getTotalHc()));
            vo.setReservedHc(nvl(job.getReservedHc()));
            vo.setConfirmedHc(nvl(job.getConfirmedHc()));
            vo.setAvailableHc(nvl(job.getAvailableHc()));
        } else {
            // 公司级聚合：listCompanyJobs → 逐个 getJobForValidation 求和（best-effort 跳过失败岗）
            List<CompanyJobDTO> jobs = listCompanyJobsOrEmpty(companyId);
            int total = 0, reserved = 0, confirmed = 0, available = 0;
            for (CompanyJobDTO j : jobs) {
                JobHcOverviewDTO d = queryJobBestEffort(j.getJobId());
                if (d == null) {
                    continue;
                }
                total += nvl(d.getTotalHc());
                reserved += nvl(d.getReservedHc());
                confirmed += nvl(d.getConfirmedHc());
                available += nvl(d.getAvailableHc());
            }
            vo.setJobId(null);
            vo.setJobTitle("全公司");
            vo.setTotalHc(total);
            vo.setReservedHc(reserved);
            vo.setConfirmedHc(confirmed);
            vo.setAvailableHc(available);
        }
        return vo;
    }

    // ==================== 催促 / 撤回 ====================

    @Override
    public void urgeOffer(Long companyId, Long offerId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);
        HrOffer offer = getOwnOffer(companyId, offerId);
        if (!OFFER_STATUS_SENT.equals(offer.getStatus())) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }

        // 频率限制：lastUrgeAt 距今 <24h 且 urgeCount>=2 → 4204
        LocalDateTime lastUrgeAt = offer.getLastUrgeAt();
        int urgeCount = offer.getUrgeCount() == null ? 0 : offer.getUrgeCount();
        if (lastUrgeAt != null && urgeCount >= 2 && lastUrgeAt.plusHours(24).isAfter(LocalDateTime.now())) {
            throw new BusinessException(HrErrorCode.OFFER_URGE_LIMIT);
        }
        if (hrOfferMapper.updateUrge(offerId) == 0) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }

        offerNotifier.notifyOfferUrge(offer.getCandidateId(), offerId,
                queryJobTitleBestEffort(offer.getJobId()), offer.getExpiresAt());
        log.info("催促Offer: offerId={}, companyId={}", offerId, companyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retractOffer(Long companyId, Long offerId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);
        HrOffer offer = getOwnOffer(companyId, offerId);
        if (!OFFER_STATUS_SENT.equals(offer.getStatus())) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }

        // 乐观锁：SENT → WITHDRAWN
        if (hrOfferMapper.updateWithdraw(offerId, OFFER_STATUS_SENT) == 0) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }

        // release HC（幂等；失败仅记日志不阻塞）
        releaseBestEffort(offer.getJobId(), companyId, offerId, RELEASE_REASON_REJECTED);
        // 投递回退 OFFERED→OFFERABLE（best-effort，失败仅记日志；C 新状态机 2026-08-07）
        revertApplicationToOfferable(offer);
        offerNotifier.notifyOfferRetracted(offer.getCandidateId(), offerId,
                queryJobTitleBestEffort(offer.getJobId()));
        log.info("撤回Offer: offerId={}, companyId={}", offerId, companyId);
    }

    // ==================== 候选人详情 / 接受 / 拒绝 ====================

    @Override
    public OfferDetailVO getOfferDetail(Long offerId) {
        HrOffer offer = getOfferOrThrow(offerId);
        requireCandidateOwner(offer);

        OfferDetailVO vo = new OfferDetailVO();
        vo.setOfferId(offer.getId());
        vo.setApplicationId(offer.getApplicationId());
        vo.setJobId(offer.getJobId());
        vo.setJobTitle(queryJobTitleBestEffort(offer.getJobId()));
        vo.setCompanyName(queryCompanyNameBestEffort(offer.getCompanyId()));
        vo.setSalary(offer.getSalary());
        vo.setEntryDate(offer.getEntryDate());
        vo.setLevel(offer.getLevel());
        vo.setRemark(offer.getRemark());
        vo.setStatus(offer.getStatus());
        vo.setStatusDesc(buildStatusDesc(offer.getStatus()));
        vo.setExpiresAt(offer.getExpiresAt());
        vo.setAcceptedAt(offer.getAcceptedAt());
        vo.setRejectedAt(offer.getRejectedAt());
        vo.setRejectReason(offer.getRejectReason());
        vo.setCreatedAt(offer.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfferActionResultVO acceptOffer(Long offerId) {
        HrOffer offer = hrOfferMapper.selectByIdForUpdate(offerId);
        if (offer == null) {
            throw new BusinessException(HrErrorCode.OFFER_NOT_FOUND);
        }
        requireCandidateOwner(offer);
        return doAcceptOffer(offer, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfferActionResultVO rejectOffer(Long offerId, String rejectReason) {
        HrOffer offer = hrOfferMapper.selectByIdForUpdate(offerId);
        if (offer == null) {
            throw new BusinessException(HrErrorCode.OFFER_NOT_FOUND);
        }
        requireCandidateOwner(offer);
        return doRejectOffer(offer, rejectReason, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfferActionResultVO acceptOfferByApplication(Long applicationId) {
        // C 端反向调用：投递状态由 C 自己改（OFFERED→OFFER_ACCEPTED），D 仅同步 hr_offer + confirm HC
        HrOffer offer = hrOfferMapper.selectByApplicationIdForUpdate(applicationId);
        if (offer == null) {
            throw new BusinessException(HrErrorCode.OFFER_NOT_FOUND);
        }
        return doAcceptOffer(offer, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfferActionResultVO rejectOfferByApplication(Long applicationId, String rejectReason) {
        // C 端反向调用：投递状态由 C 自己改（OFFERED→OFFER_DECLINED），D 仅同步 hr_offer + release HC
        HrOffer offer = hrOfferMapper.selectByApplicationIdForUpdate(applicationId);
        if (offer == null) {
            throw new BusinessException(HrErrorCode.OFFER_NOT_FOUND);
        }
        return doRejectOffer(offer, rejectReason, false);
    }

    /**
     * 接受 Offer 核心逻辑。
     * <p>syncApplication=true：D 端候选人接口（/api/v1/hr/offers/{id}/accept），联动投递 OFFERED→OFFER_ACCEPTED；</p>
     * <p>syncApplication=false：C 端投递页反向调用（/internal/offers/accept），投递状态由 C 自己改，D 只同步 hr_offer + HC。</p>
     */
    private OfferActionResultVO doAcceptOffer(HrOffer offer, boolean syncApplication) {
        Long offerId = offer.getId();
        // 幂等：已接受直接返回
        if (OFFER_STATUS_ACCEPTED.equals(offer.getStatus())) {
            return actionResult(OFFER_STATUS_ACCEPTED, APP_STATUS_OFFER_ACCEPTED);
        }
        // 状态校验
        validateAcceptable(offer);

        // confirm HC（失败 → 名额不足，Offer 保持 SENT）
        confirmHcOrThrow(offer);

        // 本地流转：SENT → ACCEPTED + accepted_at
        if (hrOfferMapper.updateAccept(offerId, OFFER_STATUS_SENT) == 0) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }
        if (syncApplication) {
            // 投递 OFFERED（待录用）→ OFFER_ACCEPTED（已录用，C 新状态机，2026-08-07）
            updateApplicationStatus(offer.getApplicationId(), APP_STATUS_OFFER_ACCEPTED, null);
        }
        notifyHrOfferResult(offer, "接受");
        log.info("候选人接受Offer: offerId={}, applicationId={}, syncApplication={}",
                offerId, offer.getApplicationId(), syncApplication);
        return actionResult(OFFER_STATUS_ACCEPTED, APP_STATUS_OFFER_ACCEPTED);
    }

    /**
     * 拒绝 Offer 核心逻辑。
     * <p>syncApplication=true：D 端候选人接口（/api/v1/hr/offers/{id}/reject），联动投递 OFFERED→OFFER_DECLINED；</p>
     * <p>syncApplication=false：C 端投递页反向调用（/internal/offers/reject），投递状态由 C 自己改，D 只同步 hr_offer + HC。</p>
     */
    private OfferActionResultVO doRejectOffer(HrOffer offer, String rejectReason, boolean syncApplication) {
        Long offerId = offer.getId();
        // 幂等：已拒绝直接返回
        if (OFFER_STATUS_REJECTED.equals(offer.getStatus())) {
            return actionResult(OFFER_STATUS_REJECTED, APP_STATUS_OFFER_DECLINED);
        }
        validateAcceptable(offer);

        // 本地流转：SENT → REJECTED + rejected_at + reject_reason
        if (hrOfferMapper.updateReject(offerId, OFFER_STATUS_SENT, rejectReason) == 0) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }
        // release HC（幂等；失败仅记日志不阻塞，对账兜底）
        releaseBestEffort(offer.getJobId(), offer.getCompanyId(), offerId, RELEASE_REASON_REJECTED);
        if (syncApplication) {
            // 投递 OFFERED（待录用）→ OFFER_DECLINED（已拒绝，C 新状态机，2026-08-07）
            updateApplicationStatus(offer.getApplicationId(), APP_STATUS_OFFER_DECLINED, null);
        }
        notifyHrOfferResult(offer, "拒绝");
        log.info("候选人拒绝Offer: offerId={}, applicationId={}, syncApplication={}",
                offerId, offer.getApplicationId(), syncApplication);
        return actionResult(OFFER_STATUS_REJECTED, APP_STATUS_OFFER_DECLINED);
    }

    // ==================== 定时任务 ====================

    @Override
    public int expireExpiredOffers() {
        List<HrOffer> expired = hrOfferMapper.selectExpiredSnt(100);
        int count = 0;
        for (HrOffer offer : expired) {
            try {
                // 条件更新：rows=0 → 已被确认/撤回，跳过
                if (hrOfferMapper.updateExpire(offer.getId(), OFFER_STATUS_SENT) == 0) {
                    continue;
                }
                releaseBestEffort(offer.getJobId(), offer.getCompanyId(), offer.getId(), RELEASE_REASON_EXPIRED);
                // 投递回退 OFFERED→OFFERABLE（best-effort；C 新状态机 2026-08-07）
                revertApplicationToOfferable(offer);
                String jobTitle = queryJobTitleBestEffort(offer.getJobId());
                offerNotifier.notifyOfferExpired(offer.getCandidateId(), offer.getId(), jobTitle);
                notifyHrExpired(offer, jobTitle);
                count++;
            } catch (Exception e) {
                log.error("Offer过期处理异常: offerId={}, error={}", offer.getId(), e.getMessage(), e);
            }
        }
        log.info("Offer过期扫描完成: processed={}", count);
        return count;
    }

    @Override
    public int reconcileHc() {
        List<HrOffer> terminal = hrOfferMapper.selectTerminalForReconcile(100);
        int count = 0;
        for (HrOffer offer : terminal) {
            try {
                // 终态中 WITHDRAWN 也按 REJECTED 语义释放（B 释放原因白名单）
                String reason = OFFER_STATUS_EXPIRED.equals(offer.getStatus())
                        ? RELEASE_REASON_EXPIRED : RELEASE_REASON_REJECTED;
                releaseHcOrThrow(offer.getJobId(), offer.getCompanyId(), offer.getId(), reason);
                hrOfferMapper.updateSyncTime(offer.getId());
                count++;
            } catch (Exception e) {
                log.error("HC补偿对账失败: offerId={}, error={}", offer.getId(), e.getMessage(), e);
            }
        }
        log.info("HC补偿对账完成: processed={}", count);
        return count;
    }

    // ==================== 私有方法 ====================

    /** 校验当前用户为本企业 HR_ADMIN，否则 4011 */
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

    /** 校验候选人本人访问，否则 401 */
    private void requireCandidateOwner(HrOffer offer) {
        Long userId = UserContext.getUserId();
        if (userId == null || !userId.equals(offer.getCandidateId())) {
            log.warn("候选人越权访问Offer: userId={}, offerId={}, candidateId={}",
                    userId, offer.getId(), offer.getCandidateId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 加载 Offer + 公司隔离（HR 侧） */
    private HrOffer getOwnOffer(Long companyId, Long offerId) {
        HrOffer offer = hrOfferMapper.selectById(offerId);
        if (offer == null || !companyId.equals(offer.getCompanyId())) {
            throw new BusinessException(HrErrorCode.OFFER_NOT_FOUND);
        }
        return offer;
    }

    /** 加载 Offer（候选人侧，不校验公司） */
    private HrOffer getOfferOrThrow(Long offerId) {
        HrOffer offer = hrOfferMapper.selectById(offerId);
        if (offer == null) {
            throw new BusinessException(HrErrorCode.OFFER_NOT_FOUND);
        }
        return offer;
    }

    /** 接受/拒绝前置校验：SENT 且未过期 */
    private void validateAcceptable(HrOffer offer) {
        if (!OFFER_STATUS_SENT.equals(offer.getStatus())) {
            throw new BusinessException(HrErrorCode.OFFER_STATUS_ERROR);
        }
        if (offer.getExpiresAt() == null || offer.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(HrErrorCode.OFFER_EXPIRED);
        }
    }

    /** 投递详情 + 跨企业隔离（4300/4301） */
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
            log.warn("跨企业发起Offer被拒绝: applicationId={}, companyId={}, appCompanyId={}",
                    applicationId, companyId, application.getCompanyId());
            throw new BusinessException(HrErrorCode.CANDIDATE_NO_PERMISSION);
        }
        return application;
    }

    /** Feign 更新投递状态（失败透传 C 侧错误码/消息） */
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

    /** 撤回/过期后投递回退 OFFERED→OFFERABLE（best-effort：失败仅记日志，不阻塞 Offer 操作） */
    private void revertApplicationToOfferable(HrOffer offer) {
        try {
            updateApplicationStatus(offer.getApplicationId(), APP_STATUS_OFFERABLE, null);
            log.info("投递回退 OFFERABLE: applicationId={}, offerId={}", offer.getApplicationId(), offer.getId());
        } catch (Exception e) {
            log.warn("投递回退 OFFERABLE 失败（不阻塞）: applicationId={}, offerId={}, error={}",
                    offer.getApplicationId(), offer.getId(), e.getMessage());
        }
    }

    /** 批量查用户信息：batch→单查→兜底空 */
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

    /** 岗位标题 map（按 jobId 去重，getJobForValidation 查询，失败降级） */
    private Map<Long, String> fetchJobTitles(List<HrOffer> offers) {
        Set<Long> jobIds = offers.stream().map(HrOffer::getJobId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> map = new HashMap<>();
        for (Long jobId : jobIds) {
            JobHcOverviewDTO d = queryJobBestEffort(jobId);
            map.put(jobId, d != null && d.getTitle() != null ? d.getTitle() : "岗位" + jobId);
        }
        return map;
    }

    /** 单个岗位标题（best-effort，失败降级"岗位"+id） */
    private String queryJobTitleBestEffort(Long jobId) {
        if (jobId == null) {
            return "";
        }
        JobHcOverviewDTO d = queryJobBestEffort(jobId);
        return d != null && d.getTitle() != null ? d.getTitle() : "岗位" + jobId;
    }

    /** 企业名称（best-effort，失败置 null） */
    private String queryCompanyNameBestEffort(Long companyId) {
        try {
            HrCompany company = hrCompanyMapper.selectById(companyId);
            return company != null ? company.getName() : null;
        } catch (Exception e) {
            log.warn("查询企业名称失败: companyId={}, error={}", companyId, e.getMessage());
            return null;
        }
    }

    /** 岗位 HC/薪资信息（best-effort，失败返回 null 不阻塞） */
    private JobHcOverviewDTO queryJobBestEffort(Long jobId) {
        if (jobId == null) {
            return null;
        }
        try {
            Result<JobHcOverviewDTO> r = jobFeignClient.getJobForValidation(jobId);
            if (r != null && r.isSuccess() && r.getData() != null) {
                return r.getData();
            }
            log.warn("查询岗位HC信息失败: jobId={}, code={}, message={}",
                    jobId, r != null ? r.getCode() : null, r != null ? r.getMessage() : null);
        } catch (Exception e) {
            log.warn("查询岗位HC信息异常: jobId={}, error={}", jobId, e.getMessage());
        }
        return null;
    }

    /** 岗位 HC 信息（失败透传 B 错误码，hc-overview 单岗用） */
    private JobHcOverviewDTO queryJobOrThrow(Long jobId) {
        Result<JobHcOverviewDTO> r;
        try {
            r = jobFeignClient.getJobForValidation(jobId);
        } catch (Exception e) {
            log.error("查询岗位HC信息异常: jobId={}, error={}", jobId, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "岗位HC信息查询失败，请稍后重试");
        }
        if (r == null || !r.isSuccess() || r.getData() == null) {
            throw new BusinessException(r != null ? r.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    r != null ? r.getMessage() : "岗位HC信息查询失败");
        }
        return r.getData();
    }

    /** 公司岗位列表（失败返回空，公司级聚合 best-effort） */
    private List<CompanyJobDTO> listCompanyJobsOrEmpty(Long companyId) {
        try {
            Result<List<CompanyJobDTO>> r = jobFeignClient.listCompanyJobs(companyId);
            if (r != null && r.isSuccess() && r.getData() != null) {
                return r.getData();
            }
            log.warn("查询企业岗位列表失败: companyId={}, code={}, message={}",
                    companyId, r != null ? r.getCode() : null, r != null ? r.getMessage() : null);
        } catch (Exception e) {
            log.warn("查询企业岗位列表异常: companyId={}, error={}", companyId, e.getMessage());
        }
        return Collections.emptyList();
    }

    /** reserve HC：B 2201(无可用HC) → 4203；其他失败透传 */
    private void reserveHc(Long jobId, Long companyId, Long offerId, Long candidateId) {
        HcReserveRequest req = new HcReserveRequest();
        req.setCompanyId(companyId);
        req.setOfferId(offerId);
        req.setCandidateId(candidateId);
        Result<Void> r = jobFeignClient.reserveHc(jobId, req);
        if (r == null || !r.isSuccess()) {
            if (r != null && r.getCode() == B_HC_NOT_AVAILABLE) {
                throw new BusinessException(HrErrorCode.OFFER_HC_INSUFFICIENT);
            }
            throw new BusinessException(r != null ? r.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    r != null ? r.getMessage() : "HC预冻结失败，请稍后重试");
        }
    }

    /** confirm HC：失败 → 名额不足（Offer 保持 SENT，不更新本地） */
    private void confirmHcOrThrow(HrOffer offer) {
        HcConfirmRequest req = new HcConfirmRequest();
        req.setCompanyId(offer.getCompanyId());
        req.setOfferId(offer.getId());
        Result<Void> r;
        try {
            r = jobFeignClient.confirmHc(offer.getJobId(), req);
        } catch (Exception e) {
            log.error("确认HC异常: offerId={}, jobId={}, error={}", offer.getId(), offer.getJobId(), e.getMessage());
            throw new BusinessException(HrErrorCode.OFFER_HC_INSUFFICIENT.getErrorCode(), "名额不足，请联系HR");
        }
        if (r == null || !r.isSuccess()) {
            log.warn("确认HC失败: offerId={}, code={}, message={}",
                    offer.getId(), r != null ? r.getCode() : null, r != null ? r.getMessage() : null);
            throw new BusinessException(HrErrorCode.OFFER_HC_INSUFFICIENT.getErrorCode(), "名额不足，请联系HR");
        }
    }

    /** release HC（失败抛异常，对账用） */
    private void releaseHcOrThrow(Long jobId, Long companyId, Long offerId, String reason) {
        HcReleaseRequest req = new HcReleaseRequest();
        req.setCompanyId(companyId);
        req.setOfferId(offerId);
        req.setReason(reason);
        Result<Void> r = jobFeignClient.releaseHc(jobId, req);
        if (r == null || !r.isSuccess()) {
            throw new BusinessException(r != null ? r.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    r != null ? r.getMessage() : "HC释放失败");
        }
    }

    /** release HC（best-effort：失败仅记日志，不阻塞；release 幂等） */
    private void releaseBestEffort(Long jobId, Long companyId, Long offerId, String reason) {
        try {
            releaseHcOrThrow(jobId, companyId, offerId, reason);
        } catch (Exception e) {
            log.warn("HC释放失败（不阻塞）: offerId={}, reason={}, error={}", offerId, reason, e.getMessage());
        }
    }

    /** 薪资软提示：B 范围为分，offer salary 为元 ×100；salaryNegotiable 时不提示 */
    private OfferCreateResultVO.SalaryWarning computeSalaryWarning(OfferCreateDTO dto, JobHcOverviewDTO job) {
        if (job == null || dto.getSalary() == null) {
            return null;
        }
        Boolean negotiable = job.getSalaryNegotiable();
        if (Boolean.TRUE.equals(negotiable)) {
            return null;
        }
        Long min = job.getSalaryMinAmount();
        Long max = job.getSalaryMaxAmount();
        if (min == null && max == null) {
            return null;
        }
        long salaryFen = (long) dto.getSalary() * 100L;
        if ((min != null && salaryFen < min) || (max != null && salaryFen > max)) {
            return OfferCreateResultVO.SalaryWarning.of("薪资超出岗位范围，请确认");
        }
        return null;
    }

    /** 过期通知本企业全部 HR_ADMIN（best-effort） */
    private void notifyHrExpired(HrOffer offer, String jobTitle) {
        try {
            List<HrCompanyMember> hrAdmins = hrCompanyMemberMapper.selectByCompanyId(offer.getCompanyId(), ROLE_HR_ADMIN, null);
            if (hrAdmins == null || hrAdmins.isEmpty()) {
                log.warn("未找到企业 HR_ADMIN，跳过Offer过期提醒: companyId={}", offer.getCompanyId());
                return;
            }
            for (HrCompanyMember admin : hrAdmins) {
                offerNotifier.notifyHrManage(admin.getUserId(), offer.getId(), jobTitle,
                        "Offer 已过期", "「" + jobTitle + "」的 Offer 已过期，请及时跟进候选人");
            }
        } catch (Exception e) {
            log.warn("Offer过期HR提醒失败: offerId={}, error={}", offer.getId(), e.getMessage());
        }
    }

    /** 候选人接受/拒绝 Offer 后通知本企业全部 HR_ADMIN（best-effort，不阻塞） */
    private void notifyHrOfferResult(HrOffer offer, String action) {
        try {
            List<HrCompanyMember> hrAdmins = hrCompanyMemberMapper.selectByCompanyId(offer.getCompanyId(), ROLE_HR_ADMIN, null);
            if (hrAdmins == null || hrAdmins.isEmpty()) {
                log.warn("未找到企业 HR_ADMIN，跳过Offer{}提醒: companyId={}", action, offer.getCompanyId());
                return;
            }
            String candidateName = "候选人" + offer.getCandidateId();
            SysUserDTO candidate = fetchUsers(Collections.singletonList(offer.getCandidateId())).get(offer.getCandidateId());
            if (candidate != null && candidate.getName() != null) {
                candidateName = candidate.getName();
            }
            String jobTitle = queryJobTitleBestEffort(offer.getJobId());
            String title = "候选人已" + action + " Offer";
            String content = "候选人「" + candidateName + "」已" + action + "「" + jobTitle + "」的 Offer，请及时跟进";
            for (HrCompanyMember admin : hrAdmins) {
                offerNotifier.notifyHrManage(admin.getUserId(), offer.getId(), jobTitle, title, content);
            }
        } catch (Exception e) {
            log.warn("Offer{} HR提醒失败: offerId={}, error={}", action, offer.getId(), e.getMessage());
        }
    }

    private OfferActionResultVO actionResult(String offerStatus, String applicationStatus) {
        OfferActionResultVO vo = new OfferActionResultVO();
        vo.setOfferStatus(offerStatus);
        vo.setApplicationStatus(applicationStatus);
        vo.setNotificationSent(false);
        return vo;
    }

    private String buildStatusDesc(String status) {
        try {
            return OfferStatus.fromCode(status).getDesc();
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

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}

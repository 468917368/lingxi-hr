package com.lingxi.hr.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.hr.domain.dto.OfferCreateDTO;
import com.lingxi.hr.domain.vo.HcOverviewVO;
import com.lingxi.hr.domain.vo.OfferActionResultVO;
import com.lingxi.hr.domain.vo.OfferCreateResultVO;
import com.lingxi.hr.domain.vo.OfferDetailVO;
import com.lingxi.hr.domain.vo.OfferVO;

/**
 * Offer 管理服务（系分 5.5.4，Day 8）
 *
 * @author 成员D
 * @since 2026-08-07
 */
public interface HrOfferService {

    /** 发起 Offer（HR_ADMIN；reserve 幂等 + 失败补偿 release） */
    OfferCreateResultVO createOffer(Long companyId, OfferCreateDTO dto);

    /** Offer 列表（HR_ADMIN；status/dateRange/jobId 筛选） */
    PageResult<OfferVO> listOffers(Long companyId, String status, String dateRange, Long jobId,
                                   Integer page, Integer size);

    /** HC 概览（HR_ADMIN；jobId 可空=公司级聚合） */
    HcOverviewVO getHcOverview(Long companyId, Long jobId);

    /** 催促确认（HR_ADMIN；24h 内 ≤2 次） */
    void urgeOffer(Long companyId, Long offerId);

    /** 撤回 Offer（HR_ADMIN；SENT→WITHDRAWN + release HC） */
    void retractOffer(Long companyId, Long offerId);

    /** 候选人查看 Offer 详情（userId==candidateId） */
    OfferDetailVO getOfferDetail(Long offerId);

    /** 候选人接受 Offer（userId==candidateId；幂等） */
    OfferActionResultVO acceptOffer(Long offerId);

    /** 候选人拒绝 Offer（userId==candidateId；幂等） */
    OfferActionResultVO rejectOffer(Long offerId, String rejectReason);

    /** C 端投递页反向接受 Offer（供 lingxi-resume /internal 调用；按 applicationId 定位 SENT Offer，幂等；仅同步 hr_offer + HC） */
    OfferActionResultVO acceptOfferByApplication(Long applicationId);

    /** C 端投递页反向拒绝 Offer（供 lingxi-resume /internal 调用；按 applicationId 定位 SENT Offer，幂等；仅同步 hr_offer + HC） */
    OfferActionResultVO rejectOfferByApplication(Long applicationId, String rejectReason);

    /** Offer 过期扫描（定时任务）：SENT 且过期 → EXPIRED + release + 通知；返回处理条数 */
    int expireExpiredOffers();

    /** HC 补偿对账（定时任务）：终态未对账 → release（幂等）→ 回写 last_sync_time；返回处理条数 */
    int reconcileHc();
}

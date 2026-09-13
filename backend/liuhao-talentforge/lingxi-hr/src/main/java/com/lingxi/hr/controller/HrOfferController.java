package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.OfferCreateDTO;
import com.lingxi.hr.domain.dto.OfferRejectDTO;
import com.lingxi.hr.domain.vo.HcOverviewVO;
import com.lingxi.hr.domain.vo.OfferActionResultVO;
import com.lingxi.hr.domain.vo.OfferCreateResultVO;
import com.lingxi.hr.domain.vo.OfferDetailVO;
import com.lingxi.hr.domain.vo.OfferVO;
import com.lingxi.hr.service.HrOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer 管理控制器（系分 5.5.4，Day 8）
 *
 * <p>接口 8 个：发起/列表/HC概览/催促/撤回（HR_ADMIN）+ 候选人详情/接受/拒绝（候选人身份，
 * 服务内 userId==candidateId 校验）。offerId 响应均为 JSON 字符串。
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/offers")
@RequiredArgsConstructor
@RequireLogin
public class HrOfferController {

    private final HrOfferService hrOfferService;

    /**
     * 发起 Offer（HR_ADMIN；投递 OFFERABLE + reserve + 薪资软提示）
     */
    @RequireRole("HR")
    @PostMapping
    public Result<OfferCreateResultVO> createOffer(@Validated @RequestBody OfferCreateDTO dto) {
        return Result.success(hrOfferService.createOffer(UserContext.getCompanyId(), dto));
    }

    /**
     * Offer 列表（HR_ADMIN；status/dateRange/jobId 筛选）
     */
    @RequireRole("HR")
    @GetMapping
    public Result<PageResult<OfferVO>> listOffers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(hrOfferService.listOffers(
                UserContext.getCompanyId(), status, dateRange, jobId, page, size));
    }

    /**
     * HC 概览（HR_ADMIN；jobId 可空=公司级聚合）
     */
    @RequireRole("HR")
    @GetMapping("/hc-overview")
    public Result<HcOverviewVO> getHcOverview(@RequestParam(required = false) Long jobId) {
        return Result.success(hrOfferService.getHcOverview(UserContext.getCompanyId(), jobId));
    }

    /**
     * 催促确认（HR_ADMIN；24h 内 ≤2 次）
     */
    @RequireRole("HR")
    @PostMapping("/{offerId}/urge")
    public Result<Void> urgeOffer(@PathVariable Long offerId) {
        hrOfferService.urgeOffer(UserContext.getCompanyId(), offerId);
        return Result.success();
    }

    /**
     * 撤回 Offer（HR_ADMIN；SENT→WITHDRAWN + release HC，不联动投递）
     */
    @RequireRole("HR")
    @PostMapping("/{offerId}/retract")
    public Result<Void> retractOffer(@PathVariable Long offerId) {
        hrOfferService.retractOffer(UserContext.getCompanyId(), offerId);
        return Result.success();
    }

    /**
     * 候选人查看 Offer 详情（userId==candidateId；跨端方案A 2026-08-07）
     */
    @GetMapping("/{offerId}")
    public Result<OfferDetailVO> getOfferDetail(@PathVariable Long offerId) {
        return Result.success(hrOfferService.getOfferDetail(offerId));
    }

    /**
     * 候选人接受 Offer（userId==candidateId；幂等）
     */
    @PostMapping("/{offerId}/accept")
    public Result<OfferActionResultVO> acceptOffer(@PathVariable Long offerId) {
        return Result.success(hrOfferService.acceptOffer(offerId));
    }

    /**
     * 候选人拒绝 Offer（userId==candidateId；幂等；body 选填）
     */
    @PostMapping("/{offerId}/reject")
    public Result<OfferActionResultVO> rejectOffer(@PathVariable Long offerId,
                                                   @RequestBody(required = false) OfferRejectDTO dto) {
        String rejectReason = dto != null ? dto.getRejectReason() : null;
        return Result.success(hrOfferService.rejectOffer(offerId, rejectReason));
    }
}

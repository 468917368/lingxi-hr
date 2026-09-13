package com.lingxi.hr.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.InternalOfferAcceptDTO;
import com.lingxi.hr.domain.dto.InternalOfferRejectDTO;
import com.lingxi.hr.service.HrOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer 内部接口（供 lingxi-resume 候选人投递页反向调用，2026-08-08）
 *
 * <p>背景：C 端候选人在投递列表直接接受/拒绝 Offer 走 lingxi-resume 的
 * {@code PUT /api/v1/applications/{id}/offer/accept|decline}，该接口只更新投递状态，
 * 不更新 hr_offer。本控制器让 C 在投递状态流转后反向同步 hr_offer + HC。</p>
 *
 * <p>路径 /internal/** 由 gateway 白名单免鉴权（与 lingxi-resume 内部接口一致），
 * 且已配置 {@code lingxi-internal-hr} 路由（/internal/offers/**）。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Slf4j
@RestController
@RequestMapping("/internal/offers")
@RequiredArgsConstructor
public class InternalOfferController {

    private final HrOfferService hrOfferService;

    /**
     * 候选人接受 Offer（按 applicationId 定位 SENT Offer；幂等）。
     * 仅同步 hr_offer + confirm HC，投递状态由 C 端调用方自己更新。
     */
    @PostMapping("/accept")
    public Result<Void> acceptOffer(@Validated @RequestBody InternalOfferAcceptDTO dto) {
        hrOfferService.acceptOfferByApplication(dto.getApplicationId());
        return Result.success();
    }

    /**
     * 候选人拒绝 Offer（按 applicationId 定位 SENT Offer；幂等）。
     * 仅同步 hr_offer + release HC，投递状态由 C 端调用方自己更新。
     */
    @PostMapping("/reject")
    public Result<Void> rejectOffer(@Validated @RequestBody InternalOfferRejectDTO dto) {
        hrOfferService.rejectOfferByApplication(dto.getApplicationId(), dto.getRejectReason());
        return Result.success();
    }
}

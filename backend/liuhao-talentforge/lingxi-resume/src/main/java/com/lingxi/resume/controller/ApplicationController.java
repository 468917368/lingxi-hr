package com.lingxi.resume.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.PageRequest;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.resume.domain.dto.ApplyRequestDTO;
import com.lingxi.resume.domain.dto.OfferRejectDTO;
import com.lingxi.resume.domain.vo.ApplicationDetailVO;
import com.lingxi.resume.domain.vo.ApplicationVO;
import com.lingxi.resume.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 投递管理控制器（C端）
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2 投递管理接口。
 * 全部接口按当前登录候选人隔离（UserContext）。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@RequireLogin
@RequireRole({"CANDIDATE"})//权限校验
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * 一键投递
     *
     * @param dto 投递请求：jobId（必传）+ resumeId（可选，为空用默认简历）
     * @return 投递结果（含岗位/企业信息）
     */
    @PostMapping
    public Result<ApplicationVO> submit(@Valid @RequestBody ApplyRequestDTO dto) {
        return Result.success(applicationService.submit(dto));
    }

    /**
     * 投递列表（分页，仅当前候选人数据）
     */
    @GetMapping
    public Result<PageResult<ApplicationVO>> list(PageRequest pageRequest) {
        return Result.success(applicationService.listApplications(pageRequest));
    }

    /**
     * 投递详情（含状态时间线，仅本人可查）
     */
    @GetMapping("/{id}")
    public Result<ApplicationDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(applicationService.getApplicationDetail(id));
    }

    /**
     * 撤回投递（仅 SUBMITTED/VIEWED/SCREENED 可撤回，面试安排后不可撤回）
     */
    @PutMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable("id") Long id) {
        applicationService.withdraw(id);
        return Result.success();
    }

    /**
     * 接受Offer（仅 OFFERED 待录用状态，终态 OFFER_ACCEPTED）
     */
    @PutMapping("/{id}/offer/accept")
    public Result<Void> acceptOffer(@PathVariable("id") Long id) {
        applicationService.acceptOffer(id);
        return Result.success();
    }

    /**
     * 拒绝Offer（仅 OFFERED 待录用状态，终态 OFFER_DECLINED；body 可选：rejectReason 透传至 hr_offer）
     */
    @PutMapping("/{id}/offer/decline")
    public Result<Void> declineOffer(@PathVariable("id") Long id,
                                     @RequestBody(required = false) OfferRejectDTO dto) {
        applicationService.declineOffer(id, dto != null ? dto.getRejectReason() : null);
        return Result.success();
    }
}

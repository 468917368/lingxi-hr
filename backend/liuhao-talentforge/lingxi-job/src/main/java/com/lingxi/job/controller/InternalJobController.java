package com.lingxi.job.controller;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.dto.request.HcConfirmRequest;
import com.lingxi.job.domain.dto.request.HcReleaseRequest;
import com.lingxi.job.domain.dto.request.HcReserveRequest;
import com.lingxi.job.domain.dto.response.HcConfirmResponse;
import com.lingxi.job.domain.dto.response.HcFlowResponse;
import com.lingxi.job.domain.dto.response.HcReleaseResponse;
import com.lingxi.job.domain.dto.response.HcReserveResponse;
import com.lingxi.job.domain.dto.response.InternalJobValidationResponse;
import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import com.lingxi.job.domain.vo.InternalCompanyJobVO;
import com.lingxi.job.domain.vo.InternalJobCardVO;
import com.lingxi.job.service.InternalJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部接口控制器（成员 A/C/D 依赖）
 * <p>认证由 InternalServiceAuthInterceptor 处理，Controller 不再校验用户身份。</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/internal/jobs")
@RequiredArgsConstructor
@Validated
public class InternalJobController {

    private final InternalJobService internalJobService;

    /**
     * 企业岗位列表（服务间接口，成员 A 按企业筛选，含全部未删除状态）
     * <p>静态 company 段不会与岗位详情路由冲突；不读 UserContext，鉴权由 InternalServiceAuthInterceptor 处理。</p>
     */
    @GetMapping("/company/{companyId:[0-9]+}")
    public Result<List<InternalCompanyJobVO>> listCompanyJobs(@PathVariable Long companyId) {
        return Result.success(internalJobService.listCompanyJobs(companyId));
    }

    /**
     * 岗位校验详情（仅按 jobId）
     */
    @GetMapping("/{jobId:[0-9]+}")
    public Result<InternalJobValidationResponse> getJobForValidation(@PathVariable Long jobId) {
        return Result.success(internalJobService.getJobForValidation(jobId));
    }

    /**
     * 内部岗位搜索
     */
    @GetMapping("/search")
    public Result<PageResult<InternalJobCardVO>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> keywords,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String industryCode,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String company,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> query = new HashMap<>();
        query.put("keyword", keyword);
        query.put("keywords", keywords);
        query.put("cityCode", cityCode);
        query.put("industryCode", industryCode);
        query.put("jobType", jobType);
        query.put("company", company);
        query.put("page", page);
        query.put("size", size);
        return Result.success(internalJobService.searchJobs(query));
    }

    /**
     * 岗位画像查询（仅按 jobId）
     */
    @GetMapping("/{jobId:[0-9]+}/requirements")
    public Result<JobRequirementResponse> getJobRequirements(@PathVariable Long jobId) {
        return Result.success(internalJobService.getJobRequirements(jobId));
    }

    /**
     * HC 预冻结
     */
    @PostMapping("/{jobId:[0-9]+}/hc/reserve")
    public Result<HcReserveResponse> reserveHc(@PathVariable Long jobId,
                                               @RequestBody @Valid HcReserveRequest request) {
        return Result.success("HC 预冻结成功", internalJobService.reserveHc(jobId, request));
    }

    /**
     * HC 确认占用
     */
    @PostMapping("/{jobId:[0-9]+}/hc/confirm")
    public Result<HcConfirmResponse> confirmHc(@PathVariable Long jobId,
                                               @RequestBody @Valid HcConfirmRequest request) {
        return Result.success("HC 确认占用成功", internalJobService.confirmHc(jobId, request));
    }

    /**
     * HC 释放回退
     */
    @PostMapping("/{jobId:[0-9]+}/hc/release")
    public Result<HcReleaseResponse> releaseHc(@PathVariable Long jobId,
                                               @RequestBody @Valid HcReleaseRequest request) {
        return Result.success("HC 释放成功", internalJobService.releaseHc(jobId, request));
    }

    /**
     * HC 流水查询（服务间接口，成员D HC 补偿对账用，系分 §21）
     * <p>静态段 /hc/flow 与现有 HC POST 路由不冲突；鉴权由 InternalServiceAuthInterceptor 处理。</p>
     */
    @GetMapping("/{jobId:[0-9]+}/hc/flow")
    public Result<HcFlowResponse> getHcFlow(@PathVariable Long jobId,
                                            @RequestParam("offerId") Long offerId) {
        return Result.success(internalJobService.getHcFlow(jobId, offerId));
    }
}

package com.lingxi.hr.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.agent.feign.JobRequirementDTO;
import com.lingxi.hr.feign.dto.CompanyJobDTO;
import com.lingxi.hr.feign.dto.HcConfirmRequest;
import com.lingxi.hr.feign.dto.HcReleaseRequest;
import com.lingxi.hr.feign.dto.HcReserveRequest;
import com.lingxi.hr.feign.dto.JobHcOverviewDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * lingxi-job Feign 客户端
 *
 * <p>HC 契约已对齐 lingxi-job {@code InternalJobController}（2026-08-07）：
 * reserve {companyId,offerId,candidateId} / confirm {companyId,offerId} /
 * release {companyId,offerId,reason}；原 {@code getHcFlow} 对应端点 B 未实现，已删除。
 *
 * @author 成员D
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-job", path = "/internal", configuration = ServiceAuthFeignConfig.class)
public interface JobFeignClient {

    @PostMapping("/jobs/{jobId}/hc/reserve")
    Result<Void> reserveHc(@PathVariable("jobId") Long jobId, @RequestBody HcReserveRequest request);

    @PostMapping("/jobs/{jobId}/hc/confirm")
    Result<Void> confirmHc(@PathVariable("jobId") Long jobId, @RequestBody HcConfirmRequest request);

    @PostMapping("/jobs/{jobId}/hc/release")
    Result<Void> releaseHc(@PathVariable("jobId") Long jobId, @RequestBody HcReleaseRequest request);

    /**
     * 岗位 HC 概览（供 hc-overview 单岗/公司级聚合 + 薪资软提示）
     */
    @GetMapping("/jobs/{jobId}")
    Result<JobHcOverviewDTO> getJobForValidation(@PathVariable("jobId") Long jobId);

    /**
     * 企业岗位列表（hc-overview 公司级聚合：取全部 jobId 后逐个查 HC）
     */
    @GetMapping("/jobs/company/{companyId}")
    Result<List<CompanyJobDTO>> listCompanyJobs(@PathVariable("companyId") Long companyId);

    /**
     * 岗位考察要点（Mock Interview 出题用）
     */
    @GetMapping("/jobs/{jobId}/requirements")
    Result<JobRequirementDTO> getJobRequirements(@PathVariable("jobId") Long jobId);
}

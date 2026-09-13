package com.lingxi.job.service;

import com.lingxi.common.domain.PageResult;
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

import java.util.List;
import java.util.Map;

/**
 * 岗位内部接口服务（成员 A/C/D 依赖）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
public interface InternalJobService {

    /**
     * 岗位校验详情（仅按 jobId 查询）
     */
    InternalJobValidationResponse getJobForValidation(Long jobId);

    /**
     * 内部岗位搜索（仅 PUBLISHED，PageHelper 分页）
     */
    PageResult<InternalJobCardVO> searchJobs(Map<String, Object> query);

    /**
     * 岗位画像查询（仅按 jobId 查询）
     */
    JobRequirementResponse getJobRequirements(Long jobId);

    /**
     * 查询企业全部未删除岗位（含各业务状态），供内部调用方按状态自行筛选。
     *
     * @param companyId 企业ID
     * @return 稳定排序的轻量岗位列表
     */
    List<InternalCompanyJobVO> listCompanyJobs(Long companyId);

    /**
     * HC 预冻结（校验请求 companyId 与岗位所属一致）
     */
    HcReserveResponse reserveHc(Long jobId, HcReserveRequest request);

    /**
     * HC 确认占用（校验请求 companyId 与岗位所属一致）
     */
    HcConfirmResponse confirmHc(Long jobId, HcConfirmRequest request);

    /**
     * HC 释放回退（校验请求 companyId 与岗位所属一致）
     */
    HcReleaseResponse releaseHc(Long jobId, HcReleaseRequest request);

    /**
     * HC 流水查询（只读，HC 对账用）；岗位不存在抛 2101，流水不存在抛 2202
     */
    HcFlowResponse getHcFlow(Long jobId, Long offerId);
}

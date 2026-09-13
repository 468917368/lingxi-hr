package com.lingxi.job.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.job.domain.dto.request.JobCreateRequest;
import com.lingxi.job.domain.dto.request.JobStatusActionRequest;
import com.lingxi.job.domain.dto.request.JobUpdateRequest;
import com.lingxi.job.domain.dto.response.JobCreateResponse;
import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.dto.response.JobStatusResponse;
import com.lingxi.job.domain.dto.response.JobUpdateResponse;
import com.lingxi.job.domain.vo.HrJobDetailVO;
import com.lingxi.job.domain.vo.HrJobListVO;
import com.lingxi.job.domain.vo.JobStatusLogVO;

import java.util.List;
import java.util.Map;

/**
 * 岗位管理服务（B端 HR 接口）
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
public interface HrJobService {

    /**
     * 创建岗位（job_post + job_profile 双表）
     */
    JobCreateResponse createJob(JobCreateRequest request);

    /**
     * 编辑岗位（仅 DRAFT，双表乐观锁）
     */
    JobUpdateResponse updateJob(Long jobId, JobUpdateRequest request);

    /**
     * 删除草稿岗位（软删除，仅 DRAFT，乐观锁 version）
     */
    void deleteJob(Long jobId, Integer version);

    /**
     * 岗位状态机变更（PUBLISH/CLOSE/REOPEN，乐观锁 version）
     */
    JobStatusResponse changeJobStatus(Long jobId, JobStatusActionRequest request);

    /**
     * HR 岗位分页列表（本企业，companyId 强制从 UserContext 取）
     */
    PageResult<HrJobListVO> listJobs(Map<String, Object> query);

    /**
     * HR 岗位详情（完整字段 + 画像，企业隔离）
     */
    HrJobDetailVO getHrJobDetail(Long jobId);

    /**
     * 岗位状态变更历史（本企业，企业隔离，created_at DESC 分页）
     *
     * @param jobId 岗位ID
     * @param page  页码（>=1，越界收敛）
     * @param size  每页条数（1~50，越界收敛）
     */
    PageResult<JobStatusLogVO> getStatusHistory(Long jobId, Integer page, Integer size);

    /**
     * HR 城市选项（全部启用城市，供创建/编辑岗位下拉，不受岗位是否存在影响）
     */
    List<JobOptionsResponse.CityOption> listCityOptions();
}

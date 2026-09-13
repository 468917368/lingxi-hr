package com.lingxi.job.mapper;

import com.lingxi.job.domain.entity.JobStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位状态变更日志表 Mapper
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Mapper
public interface JobStatusLogMapper {

    /**
     * 插入岗位状态变更日志（与 job_post 状态更新同一事务；含 reason_detail/request_id）
     *
     * @param log 状态日志
     * @return 影响行数（1=成功）
     */
    int insert(JobStatusLog log);

    /**
     * 按企业+岗位查询状态历史（created_at DESC，供 status-history 分页）
     * <p>双条件过滤：复用索引 idx_job_id(company_id, job_id, created_at) 的最左前缀，
     * 且查询层自带企业隔离防线（Service 已校验归属，此为纵深防御）。</p>
     *
     * @param companyId 企业ID
     * @param jobId     岗位ID
     * @return 该岗位全部状态日志（时间倒序）
     */
    List<JobStatusLog> selectByJobIdAndCompanyId(@Param("companyId") Long companyId, @Param("jobId") Long jobId);
}

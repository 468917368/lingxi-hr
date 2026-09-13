package com.lingxi.job.mapper;

import com.lingxi.job.domain.entity.JobProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 岗位画像表 Mapper
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Mapper
public interface JobProfileMapper {

    /**
     * 按岗位ID查询画像
     */
    JobProfile selectByJobId(@Param("jobId") Long jobId);

    /**
     * 按岗位ID+企业ID查询画像（企业隔离，HR 端使用）
     */
    JobProfile selectByJobIdAndCompanyId(@Param("jobId") Long jobId, @Param("companyId") Long companyId);

    /**
     * 插入岗位画像（useGeneratedKeys 回填 id）
     */
    int insert(JobProfile profile);

    /**
     * 按ID+企业ID乐观锁更新画像（WHERE 含 company_id/version）
     */
    int updateByIdAndVersion(JobProfile profile);
}

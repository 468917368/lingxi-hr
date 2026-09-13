package com.lingxi.admin.service;

import com.lingxi.admin.domain.dto.OfflineDTO;
import com.lingxi.admin.domain.vo.EnterpriseGroupVO;
import com.lingxi.admin.domain.vo.JobDetailVO;
import com.lingxi.admin.domain.vo.JobVO;

import java.util.List;

/**
 * 岗位管理服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminJobService {

    /**
     * 获取岗位列表（按企业分组）
     *
     * @return 岗位列表
     */
    List<EnterpriseGroupVO<JobVO>> getJobs();

    /**
     * 获取岗位详情
     *
     * @param id 岗位ID
     * @return 岗位详情
     */
    JobDetailVO getJobDetail(Long id);

    /**
     * 下架岗位
     *
     * @param jobId 岗位ID
     * @param dto   下架参数（reason + remark）
     */
    void offlineJob(Long jobId, OfflineDTO dto);
}

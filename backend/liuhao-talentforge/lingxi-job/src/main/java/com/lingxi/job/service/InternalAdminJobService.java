package com.lingxi.job.service;

import com.lingxi.job.domain.dto.request.JobOfflineRequest;
import com.lingxi.job.domain.dto.response.JobStatusResponse;

/**
 * 管理员内部岗位服务（成员 E 依赖）
 *
 * @author lingxi-team
 * @since 2026-08-04
 */
public interface InternalAdminJobService {

    /**
     * 管理员违规下架岗位（PUBLISHED/PAUSED→CLOSED，closeReason=VIOLATION，乐观锁）
     * <p>CLOSED+VIOLATION 重复调用幂等返回当前状态（不校验 version）；DRAFT/CLOSED+其他原因 → 2103。</p>
     *
     * @param jobId      岗位ID（路径参数）
     * @param request    下架请求（reason 固定 VIOLATION、remark 1~500 字、version 乐观锁）
     * @param operatorId 操作人 ID（来自 X-Operator-Id，缺省为 SYSTEM 0）
     * @return 下架后岗位状态（version/closedAt 来自 DB 真实值）
     */
    JobStatusResponse offlineJob(Long jobId, JobOfflineRequest request, Long operatorId);
}

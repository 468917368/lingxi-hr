package com.lingxi.job.service;

import com.lingxi.job.domain.entity.JobPost;

/**
 * 岗位到期自动关闭服务
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
public interface JobExpireCloseService {

    /**
     * 原子关闭单个过期岗位并落审计日志（同一事务，任一失败整体回滚）
     *
     * @param post 扫描到的过期岗位（须含 id/companyId/status）
     * @return true=已关闭并落日志；false=状态并发变化/已关闭（本轮跳过，下轮重扫）
     */
    boolean closeExpiredJob(JobPost post);
}

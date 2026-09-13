package com.lingxi.job.service.impl;

import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobStatusLog;
import com.lingxi.job.enums.CloseReasonEnum;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobStatusLogMapper;
import com.lingxi.job.service.JobExpireCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位到期自动关闭服务实现
 * <p>REQUIRES_NEW 独立事务：原子关闭岗位 + 落审计日志，任一步失败整体回滚
 * （杜绝"已关闭但无审计记录"）。由定时任务逐条调用，单条失败不影响其他记录。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExpireCloseServiceImpl implements JobExpireCloseService {

    private final JobPostMapper jobPostMapper;
    private final JobStatusLogMapper jobStatusLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean closeExpiredJob(JobPost post) {
        int rows = jobPostMapper.closeExpiredById(post.getId(), post.getStatus());
        if (rows != 1) {
            // 状态并发变化/已关闭：不抛异常，本轮跳过，下轮重新扫描
            return false;
        }
        JobStatusLog statusLog = new JobStatusLog();
        statusLog.setCompanyId(post.getCompanyId());
        statusLog.setJobId(post.getId());
        statusLog.setFromStatus(post.getStatus());
        statusLog.setToStatus("CLOSED");
        statusLog.setReason(CloseReasonEnum.EXPIRED.getCode());
        statusLog.setOperatorId(0L);        // 0=SYSTEM
        statusLog.setOperatorRole("SYSTEM");
        if (jobStatusLogMapper.insert(statusLog) != 1) {
            // 日志非 1 行 → 抛异常，REQUIRES_NEW 回滚岗位 UPDATE，杜绝"已关闭无审计"
            throw new IllegalStateException("岗位到期关闭审计日志插入失败, jobId=" + post.getId());
        }
        return true;
    }
}

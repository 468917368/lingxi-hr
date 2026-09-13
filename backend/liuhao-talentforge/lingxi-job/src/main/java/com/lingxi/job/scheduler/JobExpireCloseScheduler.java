package com.lingxi.job.scheduler;

import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.service.JobExpireCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 岗位到期自动关闭定时任务
 * <p>每小时整点扫描过期岗位，逐条调用 {@link JobExpireCloseService#closeExpiredJob}
 * （REQUIRES_NEW 独立事务），单条失败不影响其他。cron 可经 {@code JOB_EXPIRE_CLOSE_CRON}
 * 环境变量覆盖（测试环境置 "-" 禁用）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExpireCloseScheduler {

    private final JobPostMapper jobPostMapper;
    private final JobExpireCloseService jobExpireCloseService;

    @Scheduled(cron = "${job.expire-close.cron:0 0 * * * ?}")
    public void closeExpiredJobs() {
        List<JobPost> expired = jobPostMapper.listExpiredOpen();
        if (expired.isEmpty()) {
            return;
        }
        int closed = 0;
        for (JobPost post : expired) {
            try {
                if (jobExpireCloseService.closeExpiredJob(post)) {
                    closed++;
                }
            } catch (Exception e) {
                // 单条失败（含审计日志回滚）不影响其他；REQUIRES_NEW 已回滚该条
                log.error("岗位到期关闭失败，jobId={}", post.getId(), e);
            }
        }
        log.info("岗位到期自动关闭完成: 扫描 {} 个过期岗位, 关闭 {} 个", expired.size(), closed);
    }
}

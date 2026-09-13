package com.lingxi.user.task;

import com.lingxi.user.agent.recommend.JobRecommendService;
import com.lingxi.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 岗位推荐定时任务
 *
 * 每天凌晨2点执行，为活跃用户计算推荐岗位并发送通知
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRecommendTask {

    private final JobRecommendService jobRecommendService;
    private final SysUserMapper userMapper;

    /**
     * 每天凌晨2点执行岗位推荐
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyRecommend() {
        log.info("========== 开始每日岗位推荐任务 ==========");

        // 查询最近7天活跃的求职者
        List<Long> activeUsers = userMapper.findActiveCandidates(7);
        log.info("活跃用户数: {}", activeUsers.size());

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (Long userId : activeUsers) {
            try {
                boolean sent = jobRecommendService.recommendAndNotify(userId);
                if (sent) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                log.warn("用户推荐失败: userId={}", userId, e);
                failCount++;
            }
        }

        log.info("每日推荐完成: 成功={}, 跳过={}, 失败={}", successCount, skipCount, failCount);
        log.info("========== 每日岗位推荐任务结束 ==========");
    }
}

package com.lingxi.user.task;

import com.lingxi.user.agent.recommend.TalentRecommendService;
import com.lingxi.user.feign.HrCompanyFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 人才推荐定时任务
 *
 * 每天凌晨3点执行，为HR推荐匹配的求职者
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentRecommendTask {

    private final TalentRecommendService talentRecommendService;
    private final HrCompanyFeignClient hrCompanyFeignClient;

    /**
     * 每天凌晨3点执行人才推荐
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyTalentRecommend() {
        log.info("========== 开始每日人才推荐任务 ==========");

        // 查询所有HR用户
        List<Long> hrUserIds = hrCompanyFeignClient.findAllHrUserIds().getData();
        if (hrUserIds == null) hrUserIds = Collections.emptyList();
        log.info("HR用户数: {}", hrUserIds.size());

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (Long hrUserId : hrUserIds) {
            try {
                boolean sent = talentRecommendService.recommendTalent(hrUserId);
                if (sent) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                log.warn("HR人才推荐失败: hrUserId={}", hrUserId, e);
                failCount++;
            }
        }

        log.info("每日人才推荐完成: 成功={}, 跳过={}, 失败={}", successCount, skipCount, failCount);
        log.info("========== 每日人才推荐任务结束 ==========");
    }
}

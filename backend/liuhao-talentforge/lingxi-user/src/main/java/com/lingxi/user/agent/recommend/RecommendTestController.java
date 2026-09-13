package com.lingxi.user.agent.recommend;

import com.lingxi.common.domain.Result;
import com.lingxi.user.task.TalentRecommendTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 推荐功能测试接口
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/recommend")
@RequiredArgsConstructor
public class RecommendTestController {

    private final JobRecommendService jobRecommendService;
    private final TalentRecommendService talentRecommendService;
    private final TalentRecommendTask talentRecommendTask;

    /**
     * 为指定用户触发岗位推荐
     */
    @PostMapping("/job/{userId}")
    public Result<String> triggerJobRecommend(@PathVariable Long userId) {
        log.info("手动触发岗位推荐: userId={}", userId);
        boolean sent = jobRecommendService.recommendAndNotify(userId);
        return Result.success(sent ? "岗位推荐通知已发送" : "暂无推荐结果");
    }

    /**
     * 为指定HR触发人才推荐
     */
    @PostMapping("/talent/{hrUserId}")
    public Result<String> triggerTalentRecommend(@PathVariable Long hrUserId) {
        log.info("手动触发人才推荐: hrUserId={}", hrUserId);
        boolean sent = talentRecommendService.recommendTalent(hrUserId);
        return Result.success(sent ? "人才推荐通知已发送" : "暂无推荐结果");
    }

    /**
     * 触发全量人才推荐任务
     */
    @PostMapping("/talent-all")
    public Result<String> triggerAllTalentRecommend() {
        log.info("手动触发全量人才推荐任务");
        new Thread(() -> talentRecommendTask.dailyTalentRecommend()).start();
        return Result.success("全量人才推荐任务已触发");
    }
}

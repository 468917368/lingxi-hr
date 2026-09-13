package com.lingxi.hr.scheduler;

import com.lingxi.hr.service.HrOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Offer 过期扫描定时任务（系分 5.6.1）
 *
 * <p>每 5 分钟执行：SENT 且 expires_at<=NOW() → 条件更新 EXPIRED → release HC（幂等）→ 通知候选人与 HR。
 * 条件更新 rows=0 说明已被确认/撤回，跳过。单条 try-catch 隔离，失败不中断后续。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferExpireScheduler {

    private final HrOfferService hrOfferService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void expireOffers() {
        try {
            int count = hrOfferService.expireExpiredOffers();
            if (count > 0) {
                log.info("Offer过期扫描定时任务: 处理 {} 条", count);
            }
        } catch (Exception e) {
            log.error("Offer过期扫描定时任务异常: {}", e.getMessage(), e);
        }
    }
}

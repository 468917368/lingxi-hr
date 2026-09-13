package com.lingxi.hr.scheduler;

import com.lingxi.hr.service.HrOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HC 补偿对账定时任务（系分 5.6.1）
 *
 * <p>每小时执行：终态（REJECTED/EXPIRED/WITHDRAWN）且 last_sync_time IS NULL 的 Offer →
 * release HC（B 幂等，已释放无副作用）→ 回写 last_sync_time。ACCEPTED 不入对账（应 CONFIRMED，无需释放）。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HcReconcileScheduler {

    private final HrOfferService hrOfferService;

    @Scheduled(cron = "0 0 * * * ?")
    public void reconcileHc() {
        try {
            int count = hrOfferService.reconcileHc();
            if (count > 0) {
                log.info("HC补偿对账定时任务: 处理 {} 条", count);
            }
        } catch (Exception e) {
            log.error("HC补偿对账定时任务异常: {}", e.getMessage(), e);
        }
    }
}

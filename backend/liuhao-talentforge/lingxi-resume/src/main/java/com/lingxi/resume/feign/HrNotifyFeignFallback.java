package com.lingxi.resume.feign;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HrNotifyFeignClient 降级实现（通知 best-effort：失败返回 success，不阻塞投递闭环）
 *
 * @author 成员C
 * @since 2026-08-10
 */
@Slf4j
@Component
public class HrNotifyFeignFallback implements HrNotifyFeignClient {

    @Override
    public Result<Void> notifyHrNewApplication(Long applicationId) {
        log.warn("通知HR新投递 Feign 调用失败，降级跳过: applicationId={}", applicationId);
        return Result.success();
    }
}

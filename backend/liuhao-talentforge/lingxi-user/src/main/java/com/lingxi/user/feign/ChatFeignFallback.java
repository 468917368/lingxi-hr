package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天服务 Feign 降级处理
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@Component
public class ChatFeignFallback implements ChatFeignClient {

    @Override
    public Result<Map<String, Long>> createNotification(Map<String, Object> request) {
        log.warn("ChatFeignFallback.createNotification 降级: request={}", request);
        return Result.success(new HashMap<>());
    }

    @Override
    public Result<Void> deleteTodayNotifications(Long userId, String type) {
        log.warn("ChatFeignFallback.deleteTodayNotifications 降级: userId={}, type={}", userId, type);
        return Result.success();
    }
}

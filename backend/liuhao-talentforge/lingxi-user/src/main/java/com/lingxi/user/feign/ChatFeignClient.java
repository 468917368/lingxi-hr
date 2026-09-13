package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 聊天服务 Feign 客户端
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@FeignClient(name = "lingxi-chat", fallback = ChatFeignFallback.class)
public interface ChatFeignClient {

    /**
     * 创建通知
     *
     * @param request 创建请求
     * @return 通知ID
     */
    @PostMapping("/internal/notifications")
    Result<Map<String, Long>> createNotification(@RequestBody Map<String, Object> request);

    /**
     * 删除用户今日的指定类型通知
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 操作结果
     */
    @DeleteMapping("/internal/notifications/today")
    Result<Void> deleteTodayNotifications(@RequestParam("userId") Long userId,
                                          @RequestParam("type") String type);
}

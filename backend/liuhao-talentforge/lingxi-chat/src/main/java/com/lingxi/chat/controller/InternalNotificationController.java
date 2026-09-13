package com.lingxi.chat.controller;

import com.lingxi.chat.service.NotificationService;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 内部通知接口（供其他微服务调用）
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    /**
     * 创建通知（供其他服务调用）
     * POST /internal/notifications
     *
     * @param request 创建请求
     * @return 通知ID
     */
    @PostMapping
    public Result<Map<String, Long>> createNotification(@RequestBody @Valid CreateNotificationRequest request) {
        Long notificationId = notificationService.createNotification(
                request.getUserId(),
                request.getType(),
                request.getTitle(),
                request.getContent(),
                request.getTargetType(),
                request.getTargetId()
        );

        Map<String, Long> result = new HashMap<>();
        result.put("notificationId", notificationId);
        log.info("内部创建通知成功: notificationId={}, type={}", notificationId, request.getType());
        return Result.success(result);
    }

    /**
     * 删除用户今日的指定类型通知
     * DELETE /internal/notifications/today?userId=xxx&type=JOB_RECOMMEND
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 操作结果
     */
    @DeleteMapping("/today")
    public Result<Void> deleteTodayNotifications(@RequestParam("userId") Long userId,
                                                  @RequestParam("type") String type) {
        notificationService.deleteTodayNotifications(userId, type);
        log.info("删除今日通知成功: userId={}, type={}", userId, type);
        return Result.success();
    }

    /**
     * 创建通知请求
     */
    @lombok.Data
    public static class CreateNotificationRequest {
        /** 接收用户ID */
        @javax.validation.constraints.NotNull(message = "用户ID不能为空")
        private Long userId;

        /** 通知类型 */
        @javax.validation.constraints.NotBlank(message = "通知类型不能为空")
        private String type;

        /** 通知标题 */
        @javax.validation.constraints.NotBlank(message = "通知标题不能为空")
        private String title;

        /** 通知内容 */
        private String content;

        /** 关联目标类型 */
        private String targetType;

        /** 关联目标ID */
        private Long targetId;
    }
}

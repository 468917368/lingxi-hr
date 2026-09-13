package com.lingxi.chat.controller;

import com.lingxi.chat.domain.vo.NotificationVO;
import com.lingxi.chat.service.NotificationService;
import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知控制器
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 获取通知列表
     * GET /api/v1/notifications?type=APPLICATION&page=1&size=20
     *
     * @param type 通知类型（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 通知列表
     */
    @RequireLogin
    @GetMapping
    public Result<PageResult<NotificationVO>> getNotifications(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        return Result.success(notificationService.getNotifications(userId, type, page, size));
    }

    /**
     * 获取未读通知数
     * GET /api/v1/notifications/unread-count
     *
     * @return 未读通知数
     */
    @RequireLogin
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> getUnreadCount() {
        Long userId = UserContext.getUserId();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    /**
     * 标记通知已读
     * PUT /api/v1/notifications/{id}/read
     *
     * @param id 通知ID
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationService.markAsRead(userId, id);
        return Result.success();
    }

    /**
     * 全部标记已读
     * PUT /api/v1/notifications/read-all
     *
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = UserContext.getUserId();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}

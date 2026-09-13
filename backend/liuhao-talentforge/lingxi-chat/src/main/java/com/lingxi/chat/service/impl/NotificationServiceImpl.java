package com.lingxi.chat.service.impl;

import com.lingxi.chat.constant.ChatConstant;
import com.lingxi.chat.domain.entity.SysNotification;
import com.lingxi.chat.domain.vo.NotificationVO;
import com.lingxi.chat.mapper.SysNotificationMapper;
import com.lingxi.chat.service.NotificationService;
import com.lingxi.chat.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通知服务实现
 * <p>
 * 负责站内通知的创建、查询、已读标记等功能。
 * 通知通过Feign内部接口创建（由HR/Resume/Job/User服务调用），
 * 创建后通过WebSocket实时推送给在线用户。
 * </p>
 * <p>
 * 通知类型分为三类：
 * - B端（HR端）：投递通知、面试安排、Offer管理、HC预警、企业认证、人才推荐
 * - C端（求职者）：简历通知、面试邀请、Offer通知、岗位推荐
 * - 通用：系统公告
 * </p>
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SysNotificationMapper notificationMapper;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 创建通知
     * <p>
     * 写入sys_notification表，用户在线时通过WebSocket实时推送。
     * 供其他服务通过Feign调用（POST /internal/notifications）。
     * </p>
     *
     * @param userId     接收用户ID
     * @param type       通知类型（见ChatConstant.NOTIFICATION_TYPE_*）
     * @param title      通知标题
     * @param content    通知内容
     * @param targetType 关联目标类型（application/interview/offer/job）
     * @param targetId   关联目标ID
     * @return 通知ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotification(Long userId, String type, String title, String content,
                                   String targetType, Long targetId) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);

        // WebSocket推送通知给用户（如果在线）
        if (sessionManager.isOnline(userId)) {
            pushNewNotification(userId, notification);
        }

        log.info("通知创建成功: notificationId={}, userId={}, type={}", notification.getId(), userId, type);
        return notification.getId();
    }

    /**
     * 获取通知列表（分页）
     *
     * @param userId 用户ID
     * @param type   通知类型筛选（可选）
     * @param page   页码（从1开始）
     * @param size   每页条数
     * @return 分页通知列表
     */
    @Override
    public PageResult<NotificationVO> getNotifications(Long userId, String type, int page, int size) {
        int offset = (page - 1) * size;
        List<SysNotification> notifications = notificationMapper.selectByUserId(userId, type, offset, size);
        long total = notificationMapper.countByUserId(userId, type);

        List<NotificationVO> voList = notifications.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, total, page, size);
    }

    /**
     * 获取未读通知数（按类型分组）
     * <p>
     * 一次SQL查询获取所有类型的未读数，返回按类型分组的Map。
     * </p>
     *
     * @param userId 用户ID
     * @return 未读数Map（key为类型名，totalCount为总计）
     */
    @Override
    public Map<String, Long> getUnreadCount(Long userId) {
        Map<String, Long> result = new HashMap<>();

        // 一次查询获取所有类型的未读数
        List<Map<String, Object>> counts = notificationMapper.countUnreadGroupByType(userId);
        Map<String, Long> typeCounts = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : counts) {
            String type = (String) row.get("type");
            Long cnt = ((Number) row.get("cnt")).longValue();
            typeCounts.put(type, cnt);
            total += cnt;
        }

        result.put("totalCount", total);

        // B端通知统计
        result.put("newApplicationCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_NEW_APPLICATION, 0L));
        result.put("interviewScheduleCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_INTERVIEW_SCHEDULE, 0L));
        result.put("offerManageCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_OFFER_MANAGE, 0L));
        result.put("hcWarningCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_HC_WARNING, 0L));
        result.put("companyCertCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_COMPANY_CERT, 0L));
        result.put("talentRecommendCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_TALENT_RECOMMEND, 0L));

        // C端通知统计
        result.put("resumeViewedCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_RESUME_VIEWED, 0L));
        result.put("interviewInviteCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_INTERVIEW_INVITE, 0L));
        result.put("offerReceivedCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_OFFER_RECEIVED, 0L));
        result.put("jobRecommendCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_JOB_RECOMMEND, 0L));

        // 通用通知统计
        result.put("systemCount", typeCounts.getOrDefault(ChatConstant.NOTIFICATION_TYPE_SYSTEM, 0L));

        return result;
    }

    /**
     * 标记通知已读
     *
     * @param userId 当前用户ID（校验通知归属）
     * @param id     通知ID
     * @throws BusinessException 404 通知不存在 / 403 无权操作
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long userId, Long id) {
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        notificationMapper.markAsRead(id, userId);
    }

    /**
     * 标记所有通知已读
     *
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    /**
     * 删除用户今日的指定类型通知
     * <p>
     * 用于推荐通知更新：先删旧的再发新的，避免重复。
     * </p>
     *
     * @param userId 用户ID
     * @param type   通知类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTodayNotifications(Long userId, String type) {
        notificationMapper.deleteTodayByUserAndType(userId, type);
        log.debug("删除今日通知: userId={}, type={}", userId, type);
    }

    // ==================== 私有方法 ====================

    /**
     * 通知实体转VO
     *
     * @param notification 通知实体
     * @return 通知VO
     */
    private NotificationVO convertToVO(SysNotification notification) {
        return NotificationVO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .typeDesc(getTypeDesc(notification.getType()))
                .title(notification.getTitle())
                .content(notification.getContent())
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId())
                .isRead(notification.getIsRead() == 1)
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt().format(DATE_FORMATTER) : null)
                .build();
    }

    /**
     * 获取通知类型中文描述
     *
     * @param type 通知类型编码
     * @return 中文描述
     */
    private String getTypeDesc(String type) {
        if (type == null) return "";
        switch (type) {
            // B端通知
            case ChatConstant.NOTIFICATION_TYPE_NEW_APPLICATION: return "投递通知";
            case ChatConstant.NOTIFICATION_TYPE_INTERVIEW_SCHEDULE: return "面试安排";
            case ChatConstant.NOTIFICATION_TYPE_OFFER_MANAGE: return "Offer管理";
            case ChatConstant.NOTIFICATION_TYPE_HC_WARNING: return "HC预警";
            case ChatConstant.NOTIFICATION_TYPE_COMPANY_CERT: return "企业认证";
            case ChatConstant.NOTIFICATION_TYPE_TALENT_RECOMMEND: return "人才推荐";
            // C端通知
            case ChatConstant.NOTIFICATION_TYPE_RESUME_VIEWED: return "简历通知";
            case ChatConstant.NOTIFICATION_TYPE_INTERVIEW_INVITE: return "面试邀请";
            case ChatConstant.NOTIFICATION_TYPE_OFFER_RECEIVED: return "Offer通知";
            case ChatConstant.NOTIFICATION_TYPE_JOB_RECOMMEND: return "岗位推荐";
            // 通用通知
            case ChatConstant.NOTIFICATION_TYPE_SYSTEM: return "系统通知";
            default: return "通知";
        }
    }

    /**
     * 通过WebSocket推送新通知给指定用户
     *
     * @param userId       目标用户ID
     * @param notification 通知实体
     */
    private void pushNewNotification(Long userId, SysNotification notification) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", ChatConstant.NEW_NOTIFICATION);
            Map<String, Object> data = new HashMap<>();
            data.put("notificationId", notification.getId());
            data.put("type", notification.getType());
            data.put("title", notification.getTitle());
            data.put("content", notification.getContent());
            data.put("createdAt", notification.getCreatedAt().format(DATE_FORMATTER));
            msg.put("data", data);
            sessionManager.sendMessage(userId, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("推送通知失败: userId={}", userId, e);
        }
    }
}

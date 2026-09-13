package com.lingxi.chat.constant;

/**
 * 消息服务常量
 *
 * @author 成员A
 * @since 2026-08-03
 */
public class ChatConstant {

    private ChatConstant() {
    }

    // ==================== WebSocket 消息类型 ====================

    // 客户端 → 服务端
    /** 发送消息 */
    public static final String SEND_MESSAGE = "SEND_MESSAGE";
    /** 标记已读 */
    public static final String MARK_READ = "MARK_READ";
    /** 心跳请求 */
    public static final String PING = "PING";
    /** 正在输入 */
    public static final String TYPING = "TYPING";
    /** 撤回消息 */
    public static final String RECALL_MESSAGE = "RECALL_MESSAGE";

    // 服务端 → 客户端
    /** 新消息推送 */
    public static final String NEW_MESSAGE = "NEW_MESSAGE";
    /** 发送成功 */
    public static final String SEND_SUCCESS = "SEND_SUCCESS";
    /** 发送失败 */
    public static final String SEND_FAIL = "SEND_FAIL";
    /** 消息已读 */
    public static final String MESSAGES_READ = "MESSAGES_READ";
    /** 新通知 */
    public static final String NEW_NOTIFICATION = "NEW_NOTIFICATION";
    /** 未读数推送 */
    public static final String UNREAD_COUNT = "UNREAD_COUNT";
    /** 心跳响应 */
    public static final String PONG = "PONG";
    /** 消息已撤回 */
    public static final String MESSAGE_RECALLED = "MESSAGE_RECALLED";
    /** 错误 */
    public static final String ERROR = "ERROR";

    // ==================== 消息类型 ====================

    /** 纯文本消息 */
    public static final String MSG_TYPE_TEXT = "TEXT";
    /** 富文本消息 */
    public static final String MSG_TYPE_RICH_TEXT = "RICH_TEXT";
    /** 图片消息 */
    public static final String MSG_TYPE_IMAGE = "IMAGE";
    /** 文件消息 */
    public static final String MSG_TYPE_FILE = "FILE";

    // ==================== 业务消息类型 ====================

    /** 文本消息 */
    public static final String CONTENT_TYPE_TEXT = "TEXT";

    // ==================== 消息状态 ====================

    /** 已送达（自己发送，对方未读） */
    public static final String MESSAGE_STATUS_SENT = "SENT";
    /** 已送达（对方发送，未读） */
    public static final String MESSAGE_STATUS_DELIVERED = "DELIVERED";
    /** 已读 */
    public static final String MESSAGE_STATUS_READ = "READ";
    /** 发送失败 */
    public static final String MESSAGE_STATUS_FAILED = "FAILED";
    /** 图片消息 */
    public static final String CONTENT_TYPE_IMAGE = "IMAGE";
    /** 文件消息 */
    public static final String CONTENT_TYPE_FILE = "FILE";
    /** 系统消息 */
    public static final String CONTENT_TYPE_SYSTEM = "SYSTEM";
    /** 简历卡片 */
    public static final String CONTENT_TYPE_CARD_RESUME = "CARD_RESUME";
    /** 岗位卡片 */
    public static final String CONTENT_TYPE_CARD_JOB = "CARD_JOB";

    // ==================== 会话类型 ====================

    /** 投递创建会话 */
    public static final String SESSION_TYPE_JOB_APPLY = "JOB_APPLY";
    /** 邀请创建会话 */
    public static final String SESSION_TYPE_RESUME_INVITE = "RESUME_INVITE";
    /** 系统推荐 */
    public static final String SESSION_TYPE_AUTO_MATCH = "AUTO_MATCH";
    /** 普通聊天 */
    public static final String SESSION_TYPE_PRIVATE = "PRIVATE";

    // ==================== 会话状态 ====================

    /** 正常状态 */
    public static final String CONVERSATION_STATUS_ACTIVE = "ACTIVE";
    /** 已删除 */
    public static final String CONVERSATION_STATUS_DELETED = "DELETED";

    // ==================== 成员角色 ====================

    /** 求职者 */
    public static final String MEMBER_ROLE_CANDIDATE = "CANDIDATE";
    /** HR */
    public static final String MEMBER_ROLE_HR = "HR";

    // ==================== 通知类型 ====================

    // B端（HR端）通知类型
    /** 投递通知 - 收到新简历 */
    public static final String NOTIFICATION_TYPE_NEW_APPLICATION = "NEW_APPLICATION";
    /** 面试安排 - 面试确认/提醒 */
    public static final String NOTIFICATION_TYPE_INTERVIEW_SCHEDULE = "INTERVIEW_SCHEDULE";
    /** Offer管理 - Offer状态变更 */
    public static final String NOTIFICATION_TYPE_OFFER_MANAGE = "OFFER_MANAGE";
    /** HC预警 - 岗位HC即将用完 */
    public static final String NOTIFICATION_TYPE_HC_WARNING = "HC_WARNING";
    /** 企业认证 - 认证状态变更 */
    public static final String NOTIFICATION_TYPE_COMPANY_CERT = "COMPANY_CERT";
    /** 人才推荐 - 推荐匹配人才 */
    public static final String NOTIFICATION_TYPE_TALENT_RECOMMEND = "TALENT_RECOMMEND";

    // C端（求职者）通知类型
    /** 简历通知 - 简历被查看/收藏 */
    public static final String NOTIFICATION_TYPE_RESUME_VIEWED = "RESUME_VIEWED";
    /** 面试邀请 - 收到面试邀请 */
    public static final String NOTIFICATION_TYPE_INTERVIEW_INVITE = "INTERVIEW_INVITE";
    /** Offer通知 - 收到Offer */
    public static final String NOTIFICATION_TYPE_OFFER_RECEIVED = "OFFER_RECEIVED";
    /** 岗位推荐 - 推荐匹配岗位 */
    public static final String NOTIFICATION_TYPE_JOB_RECOMMEND = "JOB_RECOMMEND";

    // 通用通知类型
    /** 系统通知 - 系统公告 */
    public static final String NOTIFICATION_TYPE_SYSTEM = "SYSTEM";

    // ==================== 心跳配置 ====================

    /** 心跳超时时间（毫秒）：60秒 */
    public static final long HEARTBEAT_TIMEOUT = 60 * 1000L;

    /** 消息最大长度 */
    public static final int MAX_MESSAGE_LENGTH = 10000;

    /** 撤回时间限制：2分钟（毫秒） */
    public static final long RECALL_TIME_LIMIT_MS = 2 * 60 * 1000L;

    /** 文件最大大小：10MB */
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
}

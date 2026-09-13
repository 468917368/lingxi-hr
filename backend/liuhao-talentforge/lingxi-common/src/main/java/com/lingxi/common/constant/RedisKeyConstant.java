package com.lingxi.common.constant;

/**
 * Redis Key 常量
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 用户信息缓存 */
    public static final String USER_INFO = "user:%d";

    /** AccessToken存储（对齐系统分析文档 §2.6.1） */
    public static final String USER_TOKEN = "user:token:%d";

    /** RefreshToken存储 */
    public static final String USER_REFRESH = "user:refresh:%d";

    /** 验证码 */
    public static final String SMS_CODE = "sms:code:%s";

    /** 短信发送频率（60秒窗口） */
    public static final String SMS_FREQ = "sms:freq:%s";

    /** 短信每日发送次数（24小时窗口） */
    public static final String SMS_DAILY = "sms:daily:%s";

    /** 登录失败计数（30分钟过期） */
    public static final String LOGIN_FAIL = "login:fail:%s";

    /** 诊断任务状态（resumeId:careerBase64 → RUNNING/COMPLETED:{reportId}/FAILED:{msg}） */
    public static final String DIAGNOSIS_TASK = "diagnosis:task:%d:%s";

    /** 解析任务状态（resumeId → RUNNING/COMPLETED/FAILED:{msg}，异步化轮询用） */
    public static final String PARSE_TASK = "parse:task:%d";

    /** IP限流 - 发送验证码（60秒滑动窗口） */
    public static final String IP_RATE_SMS = "ip:rate:sms:%s";

    /** IP限流 - 登录（60秒滑动窗口） */
    public static final String IP_RATE_LOGIN = "ip:rate:login:%s";

    /** 会话数据 */
    public static final String CONVERSATION = "conversation:%d";

    /** 未读消息数 */
    public static final String UNREAD_COUNT = "unread:%d";

    /** Agent会话历史 */
    public static final String AGENT_SESSION = "agent:session:%d:%s";

    /** 分布式锁前缀 */
    public static final String LOCK_PREFIX = "lock:";

    /** Token刷新锁 */
    public static final String TOKEN_REFRESH_LOCK = "token:refresh:lock:%d";

    /**
     * 格式化Key
     */
    public static String format(String pattern, Object... args) {
        return String.format(pattern, args);
    }
}

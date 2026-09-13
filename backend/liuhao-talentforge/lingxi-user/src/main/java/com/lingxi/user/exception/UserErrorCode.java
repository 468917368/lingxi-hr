package com.lingxi.user.exception;

import com.lingxi.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户服务错误码枚举（1001-1999）
 *
 * @author 成员A
 * @since 2026-07-31
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseExceptionInterface {

    // ==================== 认证模块 1001-1099 ====================
    PHONE_INVALID(1001, "手机号格式不正确"),
    CODE_EXPIRED(1002, "验证码已过期，请重新获取"),
    LOGIN_LOCKED(1003, "账号已锁定，请30分钟后重试"),
    USER_DISABLED(1004, "账号已被禁用，请联系管理员"),
    REFRESH_TOKEN_INVALID(1005, "refreshToken无效"),
    REFRESH_TOKEN_EXPIRED(1006, "refreshToken已过期，请重新登录"),
    SMS_FREQ_LIMIT(1007, "发送过于频繁，请60秒后重试"),
    SMS_DAILY_LIMIT(1008, "今日发送次数已达上限"),
    IP_RATE_LIMIT(1009, "请求过于频繁，请稍后重试"),
    CODE_INVALID(1010, "验证码错误"),
    CAPTCHA_REQUIRED(1011, "请输入图形验证码"),
    PASSWORD_NOT_SET(1012, "该账号未设置密码，请使用验证码登录"),
    PASSWORD_ERROR(1013, "密码错误"),
    PASSWORD_STRENGTH_WEAK(1014, "密码须包含大小写字母、数字和特殊字符，长度8-20位"),
    PASSWORD_SAME_AS_OLD(1015, "新密码不能与旧密码相同"),
    REGISTER_PARAM_INVALID(1016, "注册参数不完整"),
    PHONE_ALREADY_REGISTERED(1017, "该手机号已注册，请直接登录"),
    USER_NOT_FOUND(1018, "用户不存在，请先注册"),
    SMS_SEND_FAILED(1019, "短信发送失败，请稍后重试"),
    SYSTEM_BUSY(1020, "系统繁忙，请稍后重试"),

    // ==================== 用户模块 1100-1199 ====================
    NAME_INVALID(1100, "姓名长度须在1-32字符之间"),
    NAME_CHARS_INVALID(1101, "姓名包含非法字符"),
    CITY_TOO_LONG(1102, "城市名称过长"),
    JOB_STATUS_INVALID(1103, "求职状态值不合法"),
    DESIRED_JOB_TOO_LONG(1104, "期望职位名称过长"),
    EMAIL_INVALID(1105, "邮箱格式不正确"),
    PRIVACY_TYPE_ERROR(1106, "参数类型错误"),
    AVATAR_FILE_EMPTY(1107, "请选择要上传的头像"),
    AVATAR_FILE_TOO_LARGE(1108, "头像文件大小不可超过2MB"),
    AVATAR_FORMAT_INVALID(1109, "头像仅支持jpg/png格式"),
    EMAIL_FREQ_LIMIT(1110, "邮箱验证码发送过于频繁，请稍后重试"),
    EMAIL_SEND_FAILED(1111, "邮箱验证码发送失败，请稍后重试"),
    EMAIL_NOT_VERIFIED(1112, "邮箱未验证，请先验证邮箱"),
    USER_PROFILE_NOT_FOUND(1113, "用户画像不存在"),
    NAME_UPDATE_LIMIT(1114, "姓名每月只能修改一次"),

    // ==================== 企业认证模块 1200-1299 ====================
    COMPANY_NAME_EXISTS(1200, "该企业名称已被注册"),
    LICENSE_FORMAT_INVALID(1201, "营业执照仅支持jpg/png/pdf格式"),
    INDUSTRY_REQUIRED(1202, "请选择所属行业"),
    SCALE_REQUIRED(1203, "请选择企业规模"),
    ADDRESS_TOO_LONG(1204, "企业地址过长"),
    WEBSITE_INVALID(1205, "企业官网格式不正确"),
    LICENSE_TOO_LARGE(1206, "营业执照文件大小不可超过5MB"),
    CERT_PENDING(1207, "您已有待审核的认证申请，请勿重复提交"),
    INVITE_CODE_INVALID(1210, "邀请码不存在"),
    INVITE_CODE_EXPIRED(1211, "邀请码已过期，请联系管理员重新生成"),
    ALREADY_MEMBER(1212, "您已是该企业成员，无需重复加入"),
    COMPANY_NOT_CERTIFIED(1213, "该企业尚未通过认证"),

    // ==================== Agent模块 1300-1399 ====================
    AGENT_MSG_EMPTY(1300, "消息内容不能为空"),
    AGENT_MSG_TOO_LONG(1301, "消息内容过长，请精简后重试"),
    AGENT_INJECTION_DETECTED(1302, "消息内容包含不安全信息"),
    AGENT_SENSITIVE_INFO(1303, "消息中包含敏感个人信息，请勿直接输入"),
    AGENT_CONCURRENT_LIMIT(1304, "您有正在进行的对话，请等待完成"),
    AGENT_DAILY_LIMIT(1305, "今日对话次数已达上限"),
    AGENT_TOOL_PARAM_INVALID(1310, "工具参数校验失败"),

    // ==================== WebSocket模块 1400-1499 ====================
    WS_TOKEN_INVALID(1400, "Token无效，连接被拒绝"),
    WS_CONNECTION_LIMIT(1401, "连接数已达上限"),
    WS_MSG_TOO_LARGE(1402, "消息内容过大"),
    WS_CONTENT_TYPE_INVALID(1403, "消息类型不支持"),
    WS_CONTENT_EMPTY(1404, "消息内容不能为空"),
    WS_CONTENT_TOO_LONG(1405, "消息内容过长"),
    WS_NO_PERMISSION(1406, "无权在该会话中发送消息"),
    WS_CONVERSATION_CLOSED(1407, "该会话已关闭"),

    // ==================== 通知模块 1500-1599 ====================
    NOTIFICATION_TYPE_INVALID(1500, "通知类型不合法"),
    NOTIFICATION_NO_PERMISSION(1501, "无权操作该通知"),
    NOTIFICATION_NOT_FOUND(1502, "通知不存在"),
    ;

    private final int errorCode;
    private final String errorMessage;
}

package com.lingxi.hr.exception;

import com.lingxi.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HR服务错误码枚举
 *
 * @author 成员D
 * @since 2026-07-31
 */
@Getter
@AllArgsConstructor
public enum HrErrorCode implements BaseExceptionInterface {

    // ==================== 企业认证/成员管理 4001-4012 ====================
    COMPANY_NAME_DUPLICATE(4001, "企业名称已存在"),
    MEMBER_ALREADY_EXISTS(4002, "该成员已加入企业"),
    CERT_PENDING_DUPLICATE(4003, "已有待审核的认证申请，请勿重复提交"),
    CERT_FILE_INVALID(4004, "营业执照文件格式或大小不符合要求"),
    MEMBER_ACCOUNT_DISABLED(4005, "该账号已被禁用"),
    INVITE_CODE_NOT_FOUND(4006, "邀请码不存在或格式不正确"),
    ALREADY_COMPANY_MEMBER(4008, "您已是该企业成员，无需重复加入"),
    COMPANY_NOT_APPROVED(4009, "该企业尚未通过认证，无法加入"),
    MEMBER_NOT_FOUND(4010, "成员不存在"),
    MEMBER_NO_PERMISSION(4011, "无权限操作该成员"),
    MEMBER_CANNOT_REMOVE(4012, "不能移除企业管理员"),

    // ==================== 面试模块 4100-4199 ====================
    INTERVIEW_NOT_FOUND(4100, "面试记录不存在"),
    INTERVIEW_STATUS_ERROR(4101, "面试状态不允许此操作"),
    INTERVIEW_TIME_CONFLICT(4102, "面试时间冲突"),
    INTERVIEW_EVAL_COMMENT_TOO_SHORT(4103, "评估表单校验失败（评语少于20字）"),
    INTERVIEW_ALREADY_EVALUATED(4104, "该面试已评估，请勿重复提交"),

    // ==================== Offer模块 4200-4299 ====================
    OFFER_NOT_FOUND(4200, "Offer不存在"),
    OFFER_STATUS_ERROR(4201, "Offer状态不允许此操作"),
    OFFER_EXPIRED(4202, "Offer已过期"),
    OFFER_HC_INSUFFICIENT(4203, "HC不足，无法发起Offer"),
    OFFER_URGE_LIMIT(4204, "催促频率限制（24h内最多2次）"),
    OFFER_ALREADY_EXISTS(4205, "已有待确认Offer"),

    // ==================== 候选人模块 4300-4399 ====================
    CANDIDATE_NOT_FOUND(4300, "候选人不存在"),
    CANDIDATE_NO_PERMISSION(4301, "无权查看该候选人"),
    CANDIDATE_ALREADY_PROCESSED(4302, "该候选人已处理，请勿重复操作"),
    CANDIDATE_RESUME_NOT_FOUND(4303, "候选人简历不存在"),

    // ==================== Mock Interview 4007/4014-4018 ====================
    MOCK_SESSION_NOT_FOUND(4007, "模拟面试会话不存在"),
    MOCK_SESSION_COMPLETED(40014, "面试已完成"),
    MOCK_NO_ANSWER_RECORD(40015, "暂无答题记录，无法生成报告"),
    MOCK_RESUME_FETCH_FAILED(40016, "简历不存在或无权限"),
    MOCK_GENERATE_FAILED(40017, "出题服务繁忙，请稍后重试"),
    MOCK_QUOTA_EXCEEDED(40018, "今日模拟面试次数已达上限"),
    ;

    private final int errorCode;
    private final String errorMessage;
}

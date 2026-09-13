package com.lingxi.job.exception;

import com.lingxi.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 岗位服务错误码枚举（2001-2999）
 * <p>
 * 与系分文档3.5.1节保持一致
 * </p>
 *
 * @author 成员B
 * @since 2026-07-31
 */
@Getter
@AllArgsConstructor
public enum JobErrorCode implements BaseExceptionInterface {

    // ==================== 服务认证 2000-2099 ====================
    SERVICE_AUTH_MISSING(2001, "服务身份缺失"),
    SERVICE_AUTH_FORBIDDEN(2002, "调用服务无权限"),
    PROFILE_NOT_CONFIRMED(2003, "岗位画像未确认"),

    // ==================== 岗位模块 2100-2199 ====================
    JOB_NOT_FOUND(2101, "岗位不存在或无权访问"),
    JOB_VERSION_CONFLICT(2102, "乐观锁版本冲突"),
    JOB_ILLEGAL_STATE(2103, "非法状态迁移"),
    JOB_NOT_EDITABLE(2104, "当前状态不可编辑"),
    JOB_NOT_DELETABLE(2105, "当前状态不可删除"),
    JOB_ALREADY_FAVORITED(2106, "已收藏该岗位"),
    CITY_NOT_FOUND(2107, "城市不存在"),
    CITY_DISABLED(2108, "城市已停用"),

    // ==================== HC模块 2200-2299 ====================
    HC_NOT_AVAILABLE(2201, "无可用HC"),
    HC_RESERVATION_NOT_FOUND(2202, "HC流水不存在"),
    HC_IDEMPOTENT_MISMATCH(2203, "幂等键参数不一致"),
    HC_ILLEGAL_STATE(2204, "非法HC状态迁移"),

    // ==================== AI模块 2300-2399 ====================
    AI_PARSE_UNAVAILABLE(2301, "JD解析AI不可用"),
    AGENT_OUTPUT_INVALID(2302, "Agent输出校验失败"),
    AGENT_GENERATE_FAILED(2303, "Agent无法生成题目"),
    AGENT_CONCURRENT_FULL(2304, "Agent并发已满"),
    /** 投递上下文不允许出题（覆盖：投递状态不在白名单 / 投递记录ID/岗位ID/企业ID 与请求不匹配） */
    APPLICATION_CONTEXT_INVALID(2305, "投递状态不允许出题"),

    // ==================== 题库模块 2400-2499 ====================
    QUESTION_NOT_FOUND(2401, "题目不存在或无权访问"),
    QUESTION_VERSION_CONFLICT(2402, "题目版本冲突"),
    QUESTION_ILLEGAL_STATE(2403, "非法题目状态流转"),
    QUESTION_DUPLICATE_CONTENT(2404, "同企业已存在相同题目"),
    QUESTION_REVIEW_INVALID(2406, "审核操作不合法"),
    ;

    private final int errorCode;
    private final String errorMessage;
}

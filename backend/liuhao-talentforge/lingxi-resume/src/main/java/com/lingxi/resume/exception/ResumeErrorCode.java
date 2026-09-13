package com.lingxi.resume.exception;

import com.lingxi.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简历服务错误码枚举（3001-3999）
 *
 * <p>对齐基准：《后端总体系分文档.md》4.4.1 错误码表（2026-08-01 对齐决议）。
 * 段位约定：3001-3008 简历 400 / 3101-3103 简历投递 404 / 3201-3203 投递 409 / 3501-3503 AI与Agent 500-503。
 * 注：主文档中的 3005 原预留（card_structure 为空）未纳入；现 3005 用于 FILE_EMPTY（文件内容为空）。
 *
 * @author 成员C
 * @since 2026-07-31
 */
@Getter
@AllArgsConstructor
public enum ResumeErrorCode implements BaseExceptionInterface {

    // ==================== 简历模块 3001-3008（400） ====================
    FORMAT_NOT_SUPPORTED(3001, "文件格式不支持（仅PDF/Word/图片）"),
    FILE_TOO_LARGE(3002, "文件大小超过10MB"),
    NOT_PARSED(3003, "简历未完成解析"),
    CAREER_EMPTY(3004, "职业名为空"),
    FILE_EMPTY(3005, "文件内容为空，请上传有效简历"),
    STATUS_NOT_ALLOWED(3006, "当前状态不可操作"),
    NO_DEFAULT(3007, "无默认简历"),
    JOB_NOT_OPEN(3008, "岗位不在招聘中"),

    // ==================== 资源不存在 3101-3103（404） ====================
    RESUME_NOT_FOUND(3101, "简历不存在或已删除"),
    DIAGNOSIS_NOT_FOUND(3102, "诊断报告不存在"),
    APPLICATION_NOT_FOUND(3103, "投递记录不存在"),

    // ==================== 投递模块 3201-3203（409） ====================
    FILE_COUNT_LIMIT(3201, "已达简历数量上限（5份）"),
    APPLICATION_ALREADY_EXISTS(3202, "不可重复投递"),
    STATUS_CONFLICT(3203, "状态已变更，请刷新重试"),

    // ==================== AI服务 3501-3503（500/503） ====================
    AI_SERVICE_UNAVAILABLE(3501, "AI服务暂时不可用"),
    PARSE_FAILED(3502, "简历解析失败"),
    AGENT_TIMEOUT(3503, "Agent调用超时"),
    ;

    private final int errorCode;
    private final String errorMessage;
}

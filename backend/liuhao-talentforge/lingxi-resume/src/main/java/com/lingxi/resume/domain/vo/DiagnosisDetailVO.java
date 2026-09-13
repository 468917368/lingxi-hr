package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 诊断报告详情（含 Markdown 正文）
 * <p>id 序列化为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围，前端 JSON.parse 会精度丢失。
 *
 * @author 成员C
 * @since 2026-08-06
 */
@Data
public class DiagnosisDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 诊断报告ID（字符串序列化，防前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 目标职业名 */
    private String career;

    /** 综合匹配度(0-100) */
    private BigDecimal matchScore;

    /** 诊断报告 Markdown 正文（从 MinIO 读取） */
    private String reportMd;

    /** 诊断时间 */
    private LocalDateTime createdAt;
}

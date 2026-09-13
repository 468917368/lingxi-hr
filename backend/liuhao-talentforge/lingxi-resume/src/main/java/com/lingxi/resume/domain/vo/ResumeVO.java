package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历列表视图对象
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-3 获取简历列表响应字段。
 * <p>id 序列化为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围，前端 JSON.parse 会精度丢失。
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class ResumeVO {

    /** 简历ID（字符串序列化，防前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件格式 */
    private String fileFormat;

    /** 是否默认简历 */
    private Integer isDefault;

    /** 解析状态 */
    private String parseStatus;

    /** 上传时间 */
    private LocalDateTime createdAt;
}

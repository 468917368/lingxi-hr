package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 简历上传结果视图对象
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-1 上传简历响应。
 * <p>resumeId 序列化为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围（2^53），
 * 前端 JSON.parse 会精度丢失（如 2084841093140189200 → 2084841093140189184），导致 SSE 等
 * 后续请求用错误 ID。@JsonSerialize 仅影响 JSON 输出，Java 内部仍为 Long。
 *
 * @author 成员C
 * @since 2026-08-02
 */
@Data
public class UploadResumeVO {

    /** 简历ID（字符串序列化，防前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resumeId;

    /** 原件 MinIO 访问 URL */
    private String fileUrl;

    /** 解析状态（上传成功恒为 PENDING） */
    private String parseStatus;
}

package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历详情视图对象
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-4 获取简历详情响应。
 * cardStructure 为 JSON 对象（DB 存储 JSON 字符串，序列化前反序列化为对象返回）。
 * <p>id 序列化为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围，前端 JSON.parse 会精度丢失。
 *
 * @author 成员C
 * @since 2026-08-02
 */
@Data
public class ResumeDetailVO {

    /** 简历ID（字符串序列化，防前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件格式 */
    private String fileFormat;

    /** 文件大小（字节） */
    private Integer fileSize;

    /** 是否默认简历 */
    private Integer isDefault;

    /** 解析状态 */
    private String parseStatus;

    /** Agent 解析产出的 MD 文件 URL */
    private String resumeMdUrl;

    /** 人脸照片 URL（解析提取，可为 null） */
    private String facePhotoUrl;

    /** 卡片渲染指令（JSON 对象） */
    private Object cardStructure;

    /** 联系电话（用户手动填写） */
    private String phone;

    /** 联系邮箱（用户手动填写） */
    private String email;

    /** 微信号（用户手动填写） */
    private String wechat;

    /** 姓名（用户手动填写或解析预填） */
    private String candidateName;

    /** 上传时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

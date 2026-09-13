package com.lingxi.resume.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 简历在线编辑保存请求DTO
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-5 更新简历，支持更新 cardStructure 和联系信息。
 *
 * @author 成员C
 * @since 2026-08-02
 */
@Data
public class UpdateResumeDTO {

    /** 卡片渲染指令（前端传 JSON 对象，兼容 JSON 字符串） */
    @NotNull(message = "cardStructure不能为空")
    private JsonNode cardStructure;

    /** 联系电话（用户手动填写） */
    private String phone;

    /** 联系邮箱（用户手动填写） */
    private String email;

    /** 微信号（用户手动填写） */
    private String wechat;

    /** 姓名（用户手动填写） */
    private String candidateName;
}

package com.lingxi.admin.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建/编辑公告DTO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AnnouncementDTO {

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    /**
     * 目标角色：ALL/CANDIDATE/HR/INTERVIEWER
     */
    @NotBlank(message = "目标用户不能为空")
    private String targetRole;

    /**
     * 公告内容
     */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "内容长度不能超过5000")
    private String content;

    /**
     * 状态：DRAFT(草稿) / PUBLISHED(发布)
     */
    private String status;
}

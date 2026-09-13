package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建面试安排入参（系分文档 5.5.3）
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Data
public class InterviewCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    @NotNull(message = "投递记录ID不能为空")
    private Long applicationId;

    /** 面试官用户ID */
    @NotNull(message = "面试官不能为空")
    private Long interviewerId;

    /** 预约面试时间（ISO 8601） */
    @NotNull(message = "面试时间不能为空")
    private LocalDateTime scheduledAt;

    /** 面试方式：OFFLINE/ONLINE/PHONE */
    @NotBlank(message = "面试方式不能为空")
    private String method;

    /** 面试地点或视频链接 */
    private String location;

    /** 面试备注（HR 内部使用） */
    private String remark;

    /** 给候选人的留言 */
    private String candidateNote;
}

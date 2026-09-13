package com.lingxi.chat.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 创建会话请求
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class CreateConversationRequest {

    /** 企业ID */
    @NotNull(message = "企业ID不能为空")
    private Long companyId;

    /** 求职者用户ID */
    @NotNull(message = "求职者ID不能为空")
    private Long candidateId;

    /** HR用户ID */
    @NotNull(message = "HR ID不能为空")
    private Long hrId;

    /** 关联投递记录ID */
    private Long applicationId;
}

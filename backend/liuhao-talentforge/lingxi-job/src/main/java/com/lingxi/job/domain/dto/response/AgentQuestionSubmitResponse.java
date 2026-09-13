package com.lingxi.job.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 面试官提交 AI 题目申请入库响应（阶段6.3 二期）
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Data
@AllArgsConstructor
public class AgentQuestionSubmitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 入库题目ID（软删恢复时复用原 id） */
    private Long questionId;

    /** 落库状态（恒 PENDING_REVIEW） */
    private String status;

    /** 提示消息 */
    private String message;
}

package com.lingxi.job.service;

import com.lingxi.job.domain.dto.request.AgentQuestionSubmitRequest;
import com.lingxi.job.domain.dto.response.AgentQuestionSubmitResponse;

/**
 * 面试官提交 AI 面试题申请入企业题库服务（阶段6.3 二期）
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
public interface AgentQuestionSubmitService {

    /**
     * 申请入库：校验 → 二次脱敏 → 去重/软删恢复 → 落库为 AI_GENERATED + PENDING_REVIEW
     *
     * @param jobId   出题岗位ID（路径变量，企业隔离 + 画像推导归档维度）
     * @param request 一题业务内容（不含任何控制字段）
     * @return 入库题目ID + PENDING_REVIEW 状态
     */
    AgentQuestionSubmitResponse submitToLibrary(Long jobId, AgentQuestionSubmitRequest request);
}

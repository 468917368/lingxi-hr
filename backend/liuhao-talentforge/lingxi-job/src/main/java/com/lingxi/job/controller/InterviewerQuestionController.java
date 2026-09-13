package com.lingxi.job.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.dto.request.AgentQuestionSubmitRequest;
import com.lingxi.job.domain.dto.response.AgentQuestionSubmitResponse;
import com.lingxi.job.service.AgentQuestionSubmitService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 面试官提交 AI 面试题申请入企业题库接口（阶段6.3 二期）
 * <p>类级 @RequireLogin 兜底未登录；方法级 @RequireRole("INTERVIEWER") 限定面试官
 * （HR 的题库创建/审核走既有 /api/v1/hr/questions/**，不走本接口）。
 * 请求体不含任何控制字段；companyId/createdBy/source/status/jobType/skillTags 均由 Service
 * 从 UserContext / 岗位画像推导，不信任前端。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@RestController
@RequestMapping("/api/v1/hr/jobs")
@RequiredArgsConstructor
@Validated
@RequireLogin
public class InterviewerQuestionController {

    private final AgentQuestionSubmitService agentQuestionSubmitService;

    /**
     * 单题申请入库：校验 → 二次脱敏 → 去重/软删恢复 → 落库 AI_GENERATED + PENDING_REVIEW
     *
     * @param jobId   出题岗位ID（路径变量，企业隔离 + 画像推导归档维度）
     * @param request 一题业务内容
     * @return 入库题目ID + PENDING_REVIEW
     */
    @PostMapping("/{jobId:[0-9]+}/interview-questions/submit-to-library")
    @RequireRole("INTERVIEWER")
    public Result<AgentQuestionSubmitResponse> submitToLibrary(@PathVariable Long jobId,
                                                               @RequestBody @Valid AgentQuestionSubmitRequest request) {
        return Result.success(agentQuestionSubmitService.submitToLibrary(jobId, request));
    }
}

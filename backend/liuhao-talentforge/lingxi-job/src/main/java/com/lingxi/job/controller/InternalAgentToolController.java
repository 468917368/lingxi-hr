package com.lingxi.job.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.vo.QuestionPromptVO;
import com.lingxi.job.service.InternalAgentToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 百宝箱 Agent Tool 内部回调（3 个 GET）
 * <p>
 * 业务逻辑（runToken 校验、画像/亮点/题库组装）在 {@link InternalAgentToolService}，
 * 本控制器只保留请求映射。{@code /internal/agent/**} 已排除服务认证拦截器（阶段1 预留），
 * 鉴权由 X-Run-Token 独立校验。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class InternalAgentToolController {

    private final InternalAgentToolService internalAgentToolService;

    /**
     * Tool1：岗位画像（jobId 须与 runToken 绑定一致）
     */
    @GetMapping("/jobs/{jobId:[0-9]+}/requirements")
    public Result<Map<String, Object>> getJobRequirements(
            @RequestHeader(value = "X-Run-Token", required = false) String runToken,
            @PathVariable Long jobId) {
        return Result.success(internalAgentToolService.getJobRequirements(runToken, jobId));
    }

    /**
     * Tool2：简历亮点（返回 runToken 上下文缓存的脱敏亮点）
     */
    @GetMapping("/applications/{appId:[0-9]+}/highlights")
    public Result<List<String>> getHighlights(
            @RequestHeader(value = "X-Run-Token", required = false) String runToken,
            @PathVariable Long appId) {
        return Result.success(internalAgentToolService.getHighlights(runToken, appId));
    }

    /**
     * Tool3：题库搜索（companyId/jobType 从 runToken 读取）
     */
    @GetMapping("/questions/search")
    public Result<List<QuestionPromptVO>> searchQuestions(
            @RequestHeader(value = "X-Run-Token", required = false) String runToken,
            @RequestParam(required = false) String skillTags,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty) {
        return Result.success(internalAgentToolService.searchQuestions(runToken, skillTags, questionType, difficulty));
    }
}

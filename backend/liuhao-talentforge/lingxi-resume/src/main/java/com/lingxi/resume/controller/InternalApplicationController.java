package com.lingxi.resume.controller;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.resume.domain.vo.ApplicationTrendVO;
import com.lingxi.resume.domain.vo.InternalApplicationVO;
import com.lingxi.resume.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 投递内部接口（HR端，供 lingxi-hr 模块调用）
 *
 * <p>路径/参数严格对齐 lingxi-hr {@code ResumeFeignClient} 契约：
 * <pre>
 * GET  /internal/applications/{id}                 → Result&lt;InternalApplicationVO&gt;
 * GET  /internal/applications/list?companyId&...   → Result&lt;List&lt;InternalApplicationVO&gt;&gt;
 * PUT  /internal/applications/{id}/status?status=  → Result&lt;Void&gt;
 * </pre>
 *
 * <p>{@code /internal/**} 经 Gateway AuthFilter 白名单放行（不校验 Token），
 * 无 UserContext 时操作人退化为 0L（SYSTEM 侧），状态日志仍完整记录。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/internal/applications")
@RequiredArgsConstructor
public class InternalApplicationController {

    private final ApplicationService applicationService;

    /**
     * 候选人投递详情（HR端）
     */
    @GetMapping("/{id}")
    public Result<InternalApplicationVO> getApplication(@PathVariable("id") Long id) {
        return Result.success(applicationService.getInternalById(id));
    }

    /**
     * 候选人投递列表（HR端，按企业隔离，分页 + 筛选/搜索/排序）
     *
     * @param applicationIds 投递ID集合过滤（HR 面试官本人负责范围，可选）
     */
    @GetMapping("/list")
    public Result<PageResult<InternalApplicationVO>> getApplicationList(
            @RequestParam("companyId") Long companyId,
            @RequestParam(value = "applicationIds", required = false) List<Long> applicationIds,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "jobId", required = false) Long jobId,
            @RequestParam(value = "minMatchScore", required = false) BigDecimal minMatchScore,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return Result.success(applicationService.listByCompany(
                companyId, applicationIds, jobId, status, minMatchScore, keyword, sortBy, page, pageSize));
    }

    /**
     * 更新投递状态（HR端操作投递状态的唯一入口：查看/筛选/淘汰/Offer 均走统一状态机）
     *
     * @param rejectFeedback 落选反馈JSON（可选，REJECTED 时传入；格式 {"reason":"...","suggestions":["..."]}）
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateApplicationStatus(@PathVariable("id") Long id,
                                                @RequestParam("status") String status,
                                                @RequestParam(value = "rejectFeedback", required = false) String rejectFeedback) {
        // /internal 白名单放行无 Token，UserContext 可能为空，退化为 0L（SYSTEM 侧）
        Long operatorId = UserContext.getUserId();
        applicationService.updateStatusByHr(id, status, operatorId != null ? operatorId : 0L, rejectFeedback);
        return Result.success();
    }

    /**
     * 平台投递总数（管理后台看板，成员E lingxi-admin）
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(applicationService.getApplicationCount());
    }

    /**
     * 近N天投递趋势（管理后台看板，成员E lingxi-admin，含今日投递数）
     *
     * @param days 天数，默认7
     */
    @GetMapping("/trend")
    public Result<ApplicationTrendVO> trend(@RequestParam(value = "days", defaultValue = "7") int days) {
        return Result.success(applicationService.getApplicationTrend(days));
    }

    /**
     * 更新 AI 分析结果（供 lingxi-user 异步分析后调用）
     *
     * @param id 投递记录ID
     * @param body {aiScore: 82, aiAnalysis: {...}}
     */
    @PutMapping("/{id}/ai-analysis")
    public Result<Void> updateAiAnalysis(@PathVariable("id") Long id,
                                         @RequestBody Map<String, Object> body) {
        applicationService.updateAiAnalysis(id, body);
        return Result.success();
    }
}

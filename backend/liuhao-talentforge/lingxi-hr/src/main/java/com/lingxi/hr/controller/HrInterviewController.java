package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.EvaluationDTO;
import com.lingxi.hr.domain.dto.InterviewCreateDTO;
import com.lingxi.hr.domain.vo.EvaluationDetailVO;
import com.lingxi.hr.domain.vo.EvaluationResultVO;
import com.lingxi.hr.domain.vo.InterviewVO;
import com.lingxi.hr.domain.vo.PendingEvaluationVO;
import com.lingxi.hr.service.HrInterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试协同控制器（系分文档 5.5.3，Day 4-5）
 *
 * <p>接口 8 个（AI 反馈预览 SSE 本期延后，未实现）：
 * 创建/列表/开始/取消/待评估/录入评估/查看评估。
 * 权限：本企业 ACTIVE 成员；start/cancel/评估/查看 —— HR_ADMIN 全量，INTERVIEWER 仅本人面试。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/interviews")
@RequiredArgsConstructor
@RequireLogin
public class HrInterviewController {

    private final HrInterviewService hrInterviewService;

    /**
     * 创建面试安排（投递 SCREENED→INTERVIEWING + 双端通知）
     */
    @PostMapping
    public Result<InterviewVO> createInterview(@Validated @RequestBody InterviewCreateDTO dto) {
        return Result.success(hrInterviewService.createInterview(UserContext.getCompanyId(), dto));
    }

    /**
     * 面试列表（条件分页；interviewerId 供面试官端「我的面试」；method 面试方式筛选；keyword 候选人姓名搜索）
     */
    @GetMapping
    public Result<PageResult<InterviewVO>> listInterviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long interviewerId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(hrInterviewService.listInterviews(
                UserContext.getCompanyId(), status, dateRange, jobId, interviewerId, method, keyword, page, size));
    }

    /**
     * 开始面试（PENDING/SCHEDULED → IN_PROGRESS）
     */
    @PostMapping("/{id}/start")
    public Result<Void> startInterview(@PathVariable Long id) {
        hrInterviewService.startInterview(UserContext.getCompanyId(), id);
        return Result.success();
    }

    /**
     * 取消面试（非终态 → CANCELLED + 投递回退 SCREENED）
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelInterview(@PathVariable Long id) {
        hrInterviewService.cancelInterview(UserContext.getCompanyId(), id);
        return Result.success();
    }

    /**
     * 待评估列表（IN_PROGRESS 且无正式评估）
     */
    @GetMapping("/pending-evaluations")
    public Result<List<PendingEvaluationVO>> pendingEvaluations() {
        return Result.success(hrInterviewService.pendingEvaluations(UserContext.getCompanyId()));
    }

    /**
     * 录入面试评估（草稿可覆盖转正式；正式提交触发面试 COMPLETED + 投递联动）
     */
    @PutMapping("/{interviewId}/evaluation")
    public Result<EvaluationResultVO> submitEvaluation(@PathVariable Long interviewId,
                                                       @RequestBody EvaluationDTO dto) {
        return Result.success(hrInterviewService.submitEvaluation(
                UserContext.getCompanyId(), interviewId, dto));
    }

    /**
     * 查看面试评估（前端「查看评估」，含草稿）
     */
    @GetMapping("/{interviewId}/evaluation")
    public Result<EvaluationDetailVO> getEvaluation(@PathVariable Long interviewId) {
        return Result.success(hrInterviewService.getEvaluation(UserContext.getCompanyId(), interviewId));
    }
}

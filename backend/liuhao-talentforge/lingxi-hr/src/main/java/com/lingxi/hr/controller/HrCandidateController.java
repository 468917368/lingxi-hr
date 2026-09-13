package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.MarkCandidateDTO;
import com.lingxi.hr.domain.vo.CandidateVO;
import com.lingxi.hr.domain.vo.MarkCandidateResultVO;
import com.lingxi.hr.domain.vo.TopCandidateVO;
import com.lingxi.hr.feign.dto.ResumeDetailDTO;
import com.lingxi.hr.service.HrCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 候选人管理控制器（系分文档 5.5.2，Day 3）
 *
 * <p>列表/Top5 要求本企业 ACTIVE 成员；标记为筛选决策，要求本企业 HR_ADMIN。
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/candidates")
@RequiredArgsConstructor
@RequireLogin
@RequireRole({"HR", "INTERVIEWER"})
public class HrCandidateController {

    private final HrCandidateService hrCandidateService;

    /**
     * 候选人列表（筛选/排序/分页；面试官端需传 interviewerId=本人，列表仅返回本人负责的候选人）
     */
    @GetMapping("/list")
    public Result<PageResult<CandidateVO>> listCandidates(
            @RequestParam(required = false) Long interviewerId,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer minMatchScore,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "matchScore") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(hrCandidateService.listCandidates(
                UserContext.getCompanyId(), interviewerId, jobId, status, minMatchScore, keyword, sortBy, page, size));
    }

    /**
     * Top5 高潜推荐（面试官端需传 interviewerId=本人，仅返回本人负责的候选人）
     */
    @GetMapping("/top5")
    public Result<List<TopCandidateVO>> topCandidates(@RequestParam(required = false) Long interviewerId,
                                                      @RequestParam(required = false) Long jobId) {
        return Result.success(hrCandidateService.topCandidates(UserContext.getCompanyId(), interviewerId, jobId));
    }

    /**
     * 标记候选人合适/不合适
     */
    @PutMapping("/{applicationId}/mark")
    public Result<MarkCandidateResultVO> markCandidate(@PathVariable Long applicationId,
                                                       @Validated @RequestBody MarkCandidateDTO dto) {
        return Result.success(hrCandidateService.markCandidate(UserContext.getCompanyId(), applicationId, dto));
    }

    /**
     * 查看候选人简历（HR 人才库「查看简历」按钮，需求单 v1.1）
     */
    @GetMapping("/{applicationId}/resume")
    public Result<ResumeDetailDTO> candidateResume(@PathVariable Long applicationId) {
        return Result.success(hrCandidateService.getCandidateResume(UserContext.getCompanyId(), applicationId));
    }
}

package com.lingxi.admin.controller;

import com.lingxi.admin.domain.vo.*;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.service.AdminUserService;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService userService;

    /**
     * 获取HR列表（按企业分组）
     * GET /api/v1/admin/hr
     */
    @RequireRole("ADMIN")
    @GetMapping("/hr")
    public Result<List<EnterpriseGroupVO<HRUserVO>>> getHRUsers() {
        return Result.success(userService.getHRUsers());
    }

    /**
     * 禁用HR
     * PUT /api/v1/admin/hr/{id}/disable
     */
    @RequireRole("ADMIN")
    @PutMapping("/hr/{id}/disable")
    public Result<Void> disableHR(@PathVariable Long id) {
        userService.disableUser(id, "hr");
        return Result.success();
    }

    /**
     * 启用HR
     * PUT /api/v1/admin/hr/{id}/enable
     */
    @RequireRole("ADMIN")
    @PutMapping("/hr/{id}/enable")
    public Result<Void> enableHR(@PathVariable Long id) {
        userService.enableUser(id, "hr");
        return Result.success();
    }

    /**
     * 获取面试官列表（按企业分组）
     * GET /api/v1/admin/interviewers
     */
    @RequireRole("ADMIN")
    @GetMapping("/interviewers")
    public Result<List<EnterpriseGroupVO<InterviewerVO>>> getInterviewers() {
        return Result.success(userService.getInterviewers());
    }

    /**
     * 禁用面试官
     * PUT /api/v1/admin/interviewers/{id}/disable
     */
    @RequireRole("ADMIN")
    @PutMapping("/interviewers/{id}/disable")
    public Result<Void> disableInterviewer(@PathVariable Long id) {
        userService.disableUser(id, "interviewer");
        return Result.success();
    }

    /**
     * 启用面试官
     * PUT /api/v1/admin/interviewers/{id}/enable
     */
    @RequireRole("ADMIN")
    @PutMapping("/interviewers/{id}/enable")
    public Result<Void> enableInterviewer(@PathVariable Long id) {
        userService.enableUser(id, "interviewer");
        return Result.success();
    }

    /**
     * 获取求职者列表
     * GET /api/v1/admin/candidates?status=ACTIVE&keyword=xxx&page=1&size=20
     */
    @RequireRole("ADMIN")
    @GetMapping("/candidates")
    public Result<PageResult<CandidateVO>> getCandidates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(userService.getCandidates(status, keyword, page, size));
    }

    /**
     * 获取求职者详情
     * GET /api/v1/admin/candidates/{id}
     */
    @RequireRole("ADMIN")
    @GetMapping("/candidates/{id}")
    public Result<CandidateDetailVO> getCandidateDetail(@PathVariable Long id) {
        return Result.success(userService.getCandidateDetail(id));
    }

    /**
     * 获取求职者投递记录
     * GET /api/v1/admin/candidates/{id}/applications?page=1&size=20
     */
    @RequireRole("ADMIN")
    @GetMapping("/candidates/{id}/applications")
    public Result<PageResult<ApplicationRecordVO>> getCandidateApplications(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(userService.getCandidateApplications(id, page, size));
    }

    /**
     * 禁用求职者
     * PUT /api/v1/admin/candidates/{id}/disable
     */
    @RequireRole("ADMIN")
    @PutMapping("/candidates/{id}/disable")
    public Result<Void> disableCandidate(@PathVariable Long id) {
        userService.disableUser(id, "candidate");
        return Result.success();
    }

    /**
     * 启用求职者
     * PUT /api/v1/admin/candidates/{id}/enable
     */
    @RequireRole("ADMIN")
    @PutMapping("/candidates/{id}/enable")
    public Result<Void> enableCandidate(@PathVariable Long id) {
        userService.enableUser(id, "candidate");
        return Result.success();
    }
}

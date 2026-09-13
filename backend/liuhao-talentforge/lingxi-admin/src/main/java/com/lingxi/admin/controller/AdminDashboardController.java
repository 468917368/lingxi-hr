package com.lingxi.admin.controller;

import com.lingxi.admin.domain.vo.CompanyRankVO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.vo.JobTypeVO;
import com.lingxi.admin.domain.vo.OverviewVO;
import com.lingxi.admin.domain.vo.TrendVO;
import com.lingxi.admin.service.AdminDashboardService;
import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据看板控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@RequireLogin
@RequireRole({"ADMIN"})
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    /**
     * 获取核心指标数据
     * GET /api/v1/admin/dashboard/stats
     */
    @RequireRole("ADMIN")
    @GetMapping("/stats")
    public Result<OverviewVO> getStats() {
        return Result.success(dashboardService.getStats());
    }

    /**
     * 获取投递趋势数据（近7天）
     * GET /api/v1/admin/dashboard/trend
     */
    @RequireRole("ADMIN")
    @GetMapping("/trend")
    public Result<TrendVO> getTrend() {
        return Result.success(dashboardService.getTrend());
    }

    /**
     * 获取岗位类型分布
     * GET /api/v1/admin/dashboard/job-distribution
     */
    @RequireRole("ADMIN")
    @GetMapping("/job-distribution")
    public Result<List<JobTypeVO>> getJobDistribution() {
        return Result.success(dashboardService.getJobDistribution());
    }

    /**
     * 获取企业活跃度排行
     * GET /api/v1/admin/dashboard/company-rank
     */
    @RequireRole("ADMIN")
    @GetMapping("/company-rank")
    public Result<List<CompanyRankVO>> getCompanyRank() {
        return Result.success(dashboardService.getCompanyRank());
    }
}

package com.lingxi.admin.controller;

import com.lingxi.admin.domain.dto.OfflineDTO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.vo.JobDetailVO;
import com.lingxi.admin.domain.vo.JobListVO;
import com.lingxi.admin.service.AdminJobService;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 岗位管理控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final AdminJobService jobService;

    /**
     * 获取岗位列表（按企业分组）
     * GET /api/v1/admin/jobs
     */
    @RequireRole("ADMIN")
    @GetMapping
    public Result<JobListVO> getList() {
        return Result.success(new JobListVO(jobService.getJobs()));
    }

    /**
     * 获取岗位详情
     * GET /api/v1/admin/jobs/{id}
     */
    @RequireRole("ADMIN")
    @GetMapping("/{id}")
    public Result<JobDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(jobService.getJobDetail(id));
    }

    /**
     * 下架岗位
     * PUT /api/v1/admin/jobs/{id}/offline
     */
    @RequireRole("ADMIN")
    @PutMapping("/{id}/offline")
    public Result<Void> offlineJob(@PathVariable Long id, @RequestBody(required = false) OfflineDTO dto) {
        jobService.offlineJob(id, dto);
        return Result.success();
    }
}

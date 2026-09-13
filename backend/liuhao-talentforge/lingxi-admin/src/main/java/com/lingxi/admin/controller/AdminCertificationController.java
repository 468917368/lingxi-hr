package com.lingxi.admin.controller;

import com.lingxi.admin.domain.dto.RejectDTO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.vo.CertificationDetailVO;
import com.lingxi.admin.domain.vo.CertificationStatsVO;
import com.lingxi.admin.domain.vo.CertificationVO;
import com.lingxi.admin.service.AdminCertificationService;
import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 企业认证审核控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/certifications")
@RequiredArgsConstructor
public class AdminCertificationController {

    private final AdminCertificationService certificationService;

    /**
     * 获取审核统计数据
     * GET /api/v1/admin/certifications/stats
     */
    @RequireLogin
    @RequireRole("ADMIN")
    @GetMapping("/stats")
    public Result<CertificationStatsVO> getStats() {
        return Result.success(certificationService.getCertificationStats());
    }

    /**
     * 获取审核列表
     * GET /api/v1/admin/certifications?status=PENDING&keyword=xxx&page=1&size=20
     */
    @RequireLogin
    @RequireRole("ADMIN")
    @GetMapping
    public Result<PageResult<CertificationVO>> getList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(certificationService.getCertifications(status, keyword, page, size));
    }

    /**
     * 获取审核详情
     * GET /api/v1/admin/certifications/{id}
     */
    @RequireLogin
    @RequireRole("ADMIN")
    @GetMapping("/{id}")
    public Result<CertificationDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(certificationService.getCertificationDetail(id));
    }

    /**
     * 通过审核
     * POST /api/v1/admin/certifications/{id}/approve
     */
    @RequireLogin
    @RequireRole("ADMIN")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        certificationService.approve(id);
        return Result.success();
    }

    /**
     * 拒绝审核
     * POST /api/v1/admin/certifications/{id}/reject
     */
    @RequireLogin
    @RequireRole("ADMIN")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Validated @RequestBody RejectDTO dto) {
        certificationService.reject(id, dto);
        return Result.success();
    }
}

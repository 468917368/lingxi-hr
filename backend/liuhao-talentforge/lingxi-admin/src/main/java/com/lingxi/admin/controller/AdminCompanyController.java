package com.lingxi.admin.controller;

import com.lingxi.admin.domain.vo.CompanyDetailVO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.vo.CompanyMemberVO;
import com.lingxi.admin.domain.vo.CompanyVO;
import com.lingxi.admin.service.AdminCompanyService;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业管理控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final AdminCompanyService companyService;

    /**
     * 获取企业列表
     * GET /api/v1/admin/companies?certStatus=APPROVED&keyword=xxx&page=1&size=20
     */
    @RequireRole("ADMIN")
    @GetMapping
    public Result<PageResult<CompanyVO>> getList(
            @RequestParam(required = false) String certStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(companyService.getCompanies(certStatus, keyword, page, size));
    }

    /**
     * 获取企业详情
     * GET /api/v1/admin/companies/{id}
     */
    @RequireRole("ADMIN")
    @GetMapping("/{id}")
    public Result<CompanyDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(companyService.getCompanyDetail(id));
    }

    /**
     * 获取企业成员列表
     * GET /api/v1/admin/companies/{id}/members
     */
    @RequireRole("ADMIN")
    @GetMapping("/{id}/members")
    public Result<List<CompanyMemberVO>> getMembers(@PathVariable Long id) {
        return Result.success(companyService.getCompanyMembers(id));
    }
}

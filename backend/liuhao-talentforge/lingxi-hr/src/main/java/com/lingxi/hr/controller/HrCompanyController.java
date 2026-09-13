package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.HrCompanyDTO;
import com.lingxi.hr.domain.vo.HrCertificationStatusVO;
import com.lingxi.hr.domain.vo.HrCompanyVO;
import com.lingxi.hr.service.HrCompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 企业信息/认证控制器
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/company")
@RequiredArgsConstructor
@RequireLogin
@RequireRole("HR")
public class HrCompanyController {

    private final HrCompanyService hrCompanyService;

    /**
     * 创建企业
     */
    @PostMapping("/info")
    public Result<HrCompanyVO> createCompany(@Validated @RequestBody HrCompanyDTO dto) {
        return Result.success(hrCompanyService.createCompany(dto));
    }

    /**
     * 查看企业信息
     */
    @GetMapping("/info")
    public Result<HrCompanyVO> getCompanyInfo() {
        return Result.success(hrCompanyService.getCompanyInfo(UserContext.getCompanyId()));
    }

    /**
     * 更新企业信息
     */
    @PutMapping("/info")
    public Result<Void> updateCompanyInfo(@Validated @RequestBody HrCompanyDTO dto) {
        hrCompanyService.updateCompanyInfo(UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 提交企业认证（HR 首次入驻表单）
     * <p>企业信息以 multipart 表单字段提交：name/industry/scale/address/website 等，营业执照为选填</p>
     */
    @PostMapping("/certification")
    public Result<Void> submitCertification(@Validated HrCompanyDTO dto,
                                            @RequestParam(value = "businessLicense", required = false) MultipartFile businessLicense,
                                            @RequestParam(value = "certMaterial", required = false) MultipartFile certMaterial) {
        hrCompanyService.submitCertification(dto, businessLicense, certMaterial);
        return Result.success();
    }

    /**
     * 查询企业认证状态（pending 审核页依赖）
     * <p>按 userId 反查成员关系定位企业，避免依赖注册当次 token 中缺失的 companyId</p>
     */
    @GetMapping("/certification/status")
    public Result<HrCertificationStatusVO> getCertificationStatus() {
        return Result.success(hrCompanyService.getCertificationStatus(UserContext.getUserId()));
    }
}

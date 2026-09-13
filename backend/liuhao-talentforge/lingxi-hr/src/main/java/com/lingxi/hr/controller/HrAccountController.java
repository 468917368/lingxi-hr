package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.ChangePasswordDTO;
import com.lingxi.hr.domain.dto.ChangePhoneDTO;
import com.lingxi.hr.domain.dto.SendEmailCodeDTO;
import com.lingxi.hr.domain.dto.SendPhoneCodeDTO;
import com.lingxi.hr.domain.dto.UpdateAccountProfileDTO;
import com.lingxi.hr.domain.dto.VerifyEmailDTO;
import com.lingxi.hr.domain.vo.AccountProfileVO;
import com.lingxi.hr.service.HrAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心控制器（账号信息查询/修改 + 密码修改 + 手机号/邮箱修改）
 * <p>HR_ADMIN / INTERVIEWER 共用（本企业 ACTIVE 成员）；position 不展示、操作日志不展示（2026-08-08）。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/account")
@RequiredArgsConstructor
@RequireLogin
@RequireRole({"HR", "INTERVIEWER"})
public class HrAccountController {

    private final HrAccountService hrAccountService;

    /**
     * 账号信息查询
     */
    @GetMapping("/profile")
    public Result<AccountProfileVO> getProfile() {
        return Result.success(hrAccountService.getProfile(UserContext.getUserId(), UserContext.getCompanyId()));
    }

    /**
     * 账号信息修改（name/avatar 走 lingxi-user；department 走本企业成员表；email/phone 走独立接口）
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Validated @RequestBody UpdateAccountProfileDTO dto) {
        hrAccountService.updateProfile(UserContext.getUserId(), UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 密码修改（薄转发 lingxi-user，D 校验两次一致）
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody ChangePasswordDTO dto) {
        hrAccountService.changePassword(UserContext.getUserId(), UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 发送手机号验证码（修改手机号用，验证码发到新手机号）
     */
    @PostMapping("/phone/send-code")
    public Result<Void> sendPhoneCode(@Validated @RequestBody SendPhoneCodeDTO dto) {
        hrAccountService.sendPhoneCode(UserContext.getUserId(), UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 验证并修改手机号（成功后需重新登录）
     */
    @PostMapping("/phone")
    public Result<Void> changePhone(@Validated @RequestBody ChangePhoneDTO dto) {
        hrAccountService.changePhone(UserContext.getUserId(), UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 发送邮箱验证码（修改邮箱用，验证码发到新邮箱）
     */
    @PostMapping("/email/send-code")
    public Result<Void> sendEmailCode(@Validated @RequestBody SendEmailCodeDTO dto) {
        hrAccountService.sendEmailCode(UserContext.getUserId(), UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 验证并更新邮箱
     */
    @PostMapping("/email")
    public Result<Void> verifyEmail(@Validated @RequestBody VerifyEmailDTO dto) {
        hrAccountService.verifyEmail(UserContext.getUserId(), UserContext.getCompanyId(), dto);
        return Result.success();
    }
}

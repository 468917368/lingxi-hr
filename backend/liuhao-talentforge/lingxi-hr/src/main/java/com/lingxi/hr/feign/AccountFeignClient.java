package com.lingxi.hr.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.dto.ChangePasswordFeignDTO;
import com.lingxi.hr.feign.dto.ChangePhoneFeignDTO;
import com.lingxi.hr.feign.dto.SendEmailCodeFeignDTO;
import com.lingxi.hr.feign.dto.SendPhoneCodeFeignDTO;
import com.lingxi.hr.feign.dto.UpdateUserInfoFeignDTO;
import com.lingxi.hr.feign.dto.VerifyEmailFeignDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * lingxi-user 个人中心 Feign 客户端
 * <p>
 * 调用 A 的用户侧接口（{@code /api/v1/**}），Authorization 由 lingxi-common
 * {@code FeignConfig.tokenRelayInterceptor} 透传当前 HR 的 token。
 * </p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@FeignClient(name = "lingxi-user", contextId = "accountFeignClient", path = "/api/v1")
public interface AccountFeignClient {

    /**
     * 更新用户基本信息（姓名/头像；A 校验姓名每月仅可修改一次）
     */
    @PutMapping("/user/info")
    Result<Void> updateUserInfo(@RequestBody UpdateUserInfoFeignDTO request);

    /**
     * 修改密码（A 校验旧密码正确性 + 新密码强度，成功后清除 token）
     */
    @PostMapping("/auth/change-password")
    Result<Void> changePassword(@RequestBody ChangePasswordFeignDTO request);

    /**
     * 发送手机号验证码（修改手机号用，发送到新手机号）
     */
    @PostMapping("/user/phone/send-code")
    Result<Void> sendPhoneCode(@RequestBody SendPhoneCodeFeignDTO request);

    /**
     * 验证并修改手机号（A 校验验证码，成功后清除 token 重新登录）
     */
    @PostMapping("/user/phone")
    Result<Void> changePhone(@RequestBody ChangePhoneFeignDTO request);

    /**
     * 发送邮箱验证码（修改邮箱用，发送到新邮箱）
     */
    @PostMapping("/user/email/send-code")
    Result<Void> sendEmailCode(@RequestBody SendEmailCodeFeignDTO request);

    /**
     * 验证并更新邮箱（A 校验验证码）
     */
    @PostMapping("/user/email/verify")
    Result<Void> verifyEmail(@RequestBody VerifyEmailFeignDTO request);
}

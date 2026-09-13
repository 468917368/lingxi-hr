package com.lingxi.admin.controller;

import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.dto.PasswordChangeDTO;
import com.lingxi.admin.domain.dto.ProfileUpdateDTO;
import com.lingxi.admin.domain.vo.AdminProfileVO;
import com.lingxi.admin.domain.vo.AdminUserVO;
import com.lingxi.admin.domain.vo.AuditLogVO;
import com.lingxi.admin.service.AdminProfileService;
import com.lingxi.admin.util.AdminSecurityUtil;
import com.lingxi.common.constant.RedisKeyConstant;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员个人中心控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileService profileService;
    private final RedisUtil redisUtil;

    /**
     * 获取当前管理员信息
     * GET /api/v1/admin/user/info
     */
    @RequireRole("ADMIN")
    @GetMapping("/user/info")
    public Result<AdminUserVO> getUserInfo() {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(AdminSecurityUtil.getCurrentAdminId());
        vo.setUsername(AdminSecurityUtil.getCurrentAdminUsername());
        vo.setName(AdminSecurityUtil.getCurrentAdminName());
        vo.setRole("ADMIN");
        return Result.success(vo);
    }

    /**
     * 获取管理员个人信息
     * GET /api/v1/admin/profile
     */
    @RequireRole("ADMIN")
    @GetMapping("/profile")
    public Result<AdminProfileVO> getProfile() {
        return Result.success(profileService.getProfile());
    }

    /**
     * 修改管理员个人信息
     * PUT /api/v1/admin/profile
     */
    @RequireRole("ADMIN")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Validated @RequestBody ProfileUpdateDTO dto) {
        profileService.updateProfile(dto);
        return Result.success();
    }

    /**
     * 修改管理员密码
     * PUT /api/v1/admin/password
     */
    @RequireRole("ADMIN")
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody PasswordChangeDTO dto) {
        profileService.changePassword(dto);
        return Result.success();
    }

    /**
     * 获取管理员登录日志
     * GET /api/v1/admin/login-logs
     */
    @RequireRole("ADMIN")
    @GetMapping("/login-logs")
    public Result<PageResult<AuditLogVO>> getLoginLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(profileService.getLoginLogs(page, size));
    }

    /**
     * 管理员登出
     * POST /api/v1/admin/logout
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 清除Redis中的Token
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        if (adminId != null) {
            String tokenKey = RedisKeyConstant.format(RedisKeyConstant.USER_TOKEN, adminId);
            redisUtil.delete(tokenKey);
        }
        return Result.success();
    }
}

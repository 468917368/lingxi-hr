package com.lingxi.user.controller;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.user.domain.vo.AdminUserDetailVO;
import com.lingxi.user.domain.vo.PrivacyVO;
import com.lingxi.user.domain.vo.UserInfoVO;
import com.lingxi.user.domain.vo.UserProfileVO;
import com.lingxi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内部用户接口（供其他微服务调用）
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /**
     * 获取用户信息（内部调用，含画像）
     * GET /internal/users/{id}
     */
    @GetMapping("/{id}")
    public Result<UserInfoVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserInfo(id));
    }

    /**
     * 获取用户画像（内部调用）
     * GET /internal/users/{id}/profile
     */
    @GetMapping("/{id}/profile")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long id) {
        return Result.success(userService.getUserProfile(id));
    }

    /**
     * 批量获取用户基础信息
     * GET /internal/users/batch?ids=1,2,3
     */
    @GetMapping("/batch")
    public Result<List<UserInfoVO>> getUsersByIds(@RequestParam List<Long> ids) {
        return Result.success(userService.getUserInfoByIds(ids));
    }

    /**
     * 获取用户总数
     * GET /internal/users/count
     */
    @GetMapping("/count")
    public Result<Long> getUserCount() {
        return Result.success(userService.getUserCount());
    }

    /**
     * 获取今日新增用户数
     * GET /internal/users/today-count
     */
    @GetMapping("/today-count")
    public Result<Long> getTodayUserCount() {
        return Result.success(userService.getTodayUserCount());
    }

    /**
     * 获取用户详情（管理端用）
     * GET /internal/users/detail/{id}
     */
    @GetMapping("/detail/{id}")
    public Result<AdminUserDetailVO> getAdminUserDetail(@PathVariable Long id) {
        return Result.success(userService.getAdminUserDetail(id));
    }

    /**
     * 禁用用户
     * PUT /internal/users/{id}/disable
     */
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return Result.success("用户已禁用", null);
    }

    /**
     * 启用用户
     * PUT /internal/users/{id}/enable
     */
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        userService.enableUser(id);
        return Result.success("用户已启用", null);
    }

    /**
     * 获取用户隐私设置（内部调用）
     * GET /internal/users/{id}/privacy
     */
    @GetMapping("/{id}/privacy")
    public Result<PrivacyVO> getUserPrivacy(@PathVariable Long id) {
        return Result.success(userService.getPrivacySettings(id));
    }
}

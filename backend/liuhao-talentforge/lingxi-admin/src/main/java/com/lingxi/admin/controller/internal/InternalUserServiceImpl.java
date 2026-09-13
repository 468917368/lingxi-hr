package com.lingxi.admin.controller.internal;

import com.lingxi.admin.mapper.SysUserMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.common.domain.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 内部用户接口 — 管理员直达数据库实现
 * <p>
 * 不依赖成员A，直接查询 sys_user / sys_user_profile / resume 等表
 * </p>
 *
 * @author 成员E
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserServiceImpl {

    private final SysUserMapper sysUserMapper;

    // ==================== 1. 用户总数 ====================

    @GetMapping("/users/count")
    public Result<Long> getUserCount() {
        return Result.success(sysUserMapper.countUsers());
    }

    // ==================== 2. 今日新增用户 ====================

    @GetMapping("/users/today-count")
    public Result<Long> getTodayUserCount() {
        return Result.success(sysUserMapper.countTodayUsers());
    }

    // ==================== 3. 求职者列表 ====================

    @GetMapping("/candidates")
    public Result<Map<String, Object>> getCandidates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        List<Map<String, Object>> list = sysUserMapper.selectCandidates(status, keyword);

        // 简单分页
        int total = list.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = fromIndex < total ? list.subList(fromIndex, toIndex) : new ArrayList<>();

        // 脱敏手机号
        for (Map<String, Object> item : pageList) {
            String phone = (String) item.get("phone");
            if (phone != null && phone.length() == 11) {
                item.put("phone", phone.substring(0, 3) + "****" + phone.substring(7));
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", pageList);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    // ==================== 4. 用户详情 ====================

    @GetMapping("/users/{id}")
    public Result<Map<String, Object>> getUserDetail(@PathVariable Long id) {
        Map<String, Object> user = sysUserMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        // 求职者补充画像
        if ("CANDIDATE".equals(user.get("role"))) {
            Map<String, Object> profile = sysUserMapper.selectProfileByUserId(id);
            if (profile != null) {
                user.putAll(profile);
            }
            user.put("resumeCount", sysUserMapper.countResumesByUserId(id));
            user.put("applicationCount", sysUserMapper.countApplicationsByUserId(id));
        }

        return Result.success(user);
    }

    // ==================== 5. 禁用用户 ====================

    @PutMapping("/users/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        int rows = sysUserMapper.updateStatus(id, "DISABLED");
        if (rows == 0) {
            return Result.error(404, "用户不存在或已被禁用");
        }
        return Result.success();
    }

    // ==================== 6. 启用用户 ====================

    @PutMapping("/users/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        int rows = sysUserMapper.updateStatus(id, "ACTIVE");
        if (rows == 0) {
            return Result.error(404, "用户不存在或已是正常状态");
        }
        return Result.success();
    }
}

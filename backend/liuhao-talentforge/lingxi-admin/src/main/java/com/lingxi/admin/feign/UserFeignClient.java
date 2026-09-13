package com.lingxi.admin.feign;

import com.lingxi.admin.feign.fallback.UserFeignFallbackFactory;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户服务Feign客户端
 *
 * @author 成员E
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-user", path = "/internal", fallbackFactory = UserFeignFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 获取用户总数
     */
    @GetMapping("/users/count")
    Result<Long> getUserCount();

    /**
     * 获取今日新增用户数
     */
    @GetMapping("/users/today-count")
    Result<Long> getTodayUserCount();

    /**
     * 获取求职者列表
     */
    @GetMapping("/candidates")
    Result<Map<String, Object>> getCandidates(
            @RequestParam("status") String status,
            @RequestParam("keyword") String keyword,
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size);

    /**
     * 获取用户详情
     */
    @GetMapping("/users/detail/{id}")
    Result<Map<String, Object>> getUserDetail(@PathVariable("id") Long id);

    /**
     * 禁用用户
     */
    @PutMapping("/users/{id}/disable")
    Result<Void> disableUser(@PathVariable("id") Long id);

    /**
     * 启用用户
     */
    @PutMapping("/users/{id}/enable")
    Result<Void> enableUser(@PathVariable("id") Long id);
}

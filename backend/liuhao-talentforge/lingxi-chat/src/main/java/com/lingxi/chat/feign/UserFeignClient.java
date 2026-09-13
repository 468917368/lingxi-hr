package com.lingxi.chat.feign;

import com.lingxi.chat.feign.fallback.UserFeignFallback;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 用户服务Feign客户端
 *
 * @author 成员A
 * @since 2026-08-03
 */
@FeignClient(name = "lingxi-user", fallback = UserFeignFallback.class)
public interface UserFeignClient {

    /**
     * 获取用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/internal/users/{id}")
    Result<Map<String, Object>> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户基础信息
     *
     * @param ids 用户ID列表
     * @return 用户信息列表
     */
    @GetMapping("/internal/users/batch")
    Result<List<Map<String, Object>>> getUsersByIds(@RequestParam("ids") List<Long> ids);
}

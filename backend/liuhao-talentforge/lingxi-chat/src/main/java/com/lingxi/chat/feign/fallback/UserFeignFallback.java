package com.lingxi.chat.feign.fallback;

import com.lingxi.chat.feign.UserFeignClient;
import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务Feign降级处理
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@Component
public class UserFeignFallback implements UserFeignClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> getUserById(Long id) {
        log.warn("Feign调用用户服务失败，尝试直接HTTP调用: userId={}", id);
        try {
            // 直接调用用户服务
            String url = "http://localhost:8086/internal/users/" + id;
            Result<Map<String, Object>> result = restTemplate.getForObject(url, Result.class);
            if (result != null && result.getData() != null) {
                log.info("直接HTTP调用成功: userId={}", id);
                return result;
            }
        } catch (Exception e) {
            log.error("直接HTTP调用也失败: userId={}", id, e);
        }

        // 返回默认值
        Map<String, Object> user = new HashMap<>();
        user.put("id", id);
        user.put("name", "用户");
        user.put("avatar", "/default-avatar.png");
        user.put("role", "CANDIDATE");
        return Result.success(user);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<List<Map<String, Object>>> getUsersByIds(List<Long> ids) {
        log.warn("Feign批量调用用户服务失败，尝试直接HTTP调用: userIds={}", ids);
        try {
            // 直接调用用户服务
            String url = "http://localhost:8086/internal/users/batch?ids=" + String.join(",", ids.stream().map(String::valueOf).toArray(String[]::new));
            Result<List<Map<String, Object>>> result = restTemplate.getForObject(url, Result.class);
            if (result != null && result.getData() != null) {
                log.info("直接HTTP批量调用成功: userIds={}", ids);
                return result;
            }
        } catch (Exception e) {
            log.error("直接HTTP批量调用也失败: userIds={}", ids, e);
        }

        // 返回默认值
        List<Map<String, Object>> users = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> user = new HashMap<>();
            user.put("id", id);
            user.put("name", "用户");
            user.put("avatar", "/default-avatar.png");
            user.put("role", "CANDIDATE");
            users.add(user);
        }
        return Result.success(users);
    }
}

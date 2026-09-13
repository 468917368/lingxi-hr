package com.lingxi.admin.feign.fallback;

import com.lingxi.admin.feign.UserFeignClient;
import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 用户服务降级工厂
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Slf4j
@Component
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient 调用失败: {}", cause.getMessage());

        return new UserFeignClient() {
            @Override
            public Result<Long> getUserCount() {
                log.warn("getUserCount 降级返回 0");
                return Result.success(0L);
            }

            @Override
            public Result<Long> getTodayUserCount() {
                log.warn("getTodayUserCount 降级返回 0");
                return Result.success(0L);
            }

            @Override
            public Result<Map<String, Object>> getCandidates(String status, String keyword, Integer page, Integer size) {
                log.warn("getCandidates 降级返回null");
                return null;
            }

            @Override
            public Result<Map<String, Object>> getUserDetail(Long id) {
                log.warn("getUserDetail 降级返回null");
                return null;
            }

            @Override
            public Result<Void> disableUser(Long id) {
                log.error("disableUser 降级，操作失败");
                return Result.error(503, "用户服务暂时不可用");
            }

            @Override
            public Result<Void> enableUser(Long id) {
                log.error("enableUser 降级，操作失败");
                return Result.error(503, "用户服务暂时不可用");
            }
        };
    }
}

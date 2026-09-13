package com.lingxi.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * IP限流工具类（Redis滑动窗口）
 * <p>
 * 使用ZSET实现滑动窗口限流，支持按IP+接口维度限流。
 * 配合Sentinel在Gateway层做粗粒度流控。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitUtil {

    private final StringRedisTemplate redisTemplate;

    /**
     * IP限流检查
     *
     * @param key    限流Key（如 ip:rate:sms:{ip}）
     * @param limit  窗口内最大请求数
     * @param window 窗口大小（秒）
     * @return true=允许, false=限流
     */
    public boolean isAllowed(String key, int limit, int window) {
        long now = System.currentTimeMillis();
        long windowStart = now - window * 1000L;

        try {
            // 使用ZSET实现滑动窗口
            String member = now + ":" + System.nanoTime();
            redisTemplate.opsForZSet().add(key, member, now);
            // 移除窗口外的记录
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            // 获取窗口内请求数
            Long count = redisTemplate.opsForZSet().zCard(key);
            // 设置过期时间
            redisTemplate.expire(key, window, TimeUnit.SECONDS);

            return count != null && count <= limit;
        } catch (Exception e) {
            // Redis异常时降级放行
            log.warn("Redis限流检查异常，降级放行: key={}", key, e);
            return true;
        }
    }
}

package com.lingxi.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 幂等控制工具类
 * <p>
 * 基于Redis实现接口幂等性校验，防止重复提交。
 * 使用方式：
 * 1. 前端生成唯一流水号（idempotentKey）
 * 2. 调用 check() 校验是否重复
 * 3. 业务完成后调用 delete() 清除标记
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "idempotent:";

    /**
     * 校验是否重复提交
     *
     * @param key      幂等键（前端传入的唯一流水号）
     * @param expireMs 过期时间（毫秒）
     * @return true=首次提交，false=重复提交
     */
    public boolean check(String key, long expireMs) {
        String redisKey = KEY_PREFIX + key;
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", expireMs, TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(result)) {
            log.debug("幂等校验通过: key={}", key);
            return true;
        }
        log.warn("重复提交: key={}", key);
        return false;
    }

    /**
     * 校验是否重复提交（默认5分钟过期）
     */
    public boolean check(String key) {
        return check(key, 5 * 60 * 1000L);
    }

    /**
     * 删除幂等标记（业务完成后调用）
     */
    public void delete(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}

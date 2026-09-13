package com.lingxi.user.agent;

import com.lingxi.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Agent 频率限制器
 * 限制：每用户每分钟最多 10 次对话
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Component
@RequiredArgsConstructor
public class AgentRateLimiter {

    private final RedisUtil redisUtil;

    private static final int MAX_PER_MINUTE = 10;
    private static final String PREFIX = "agent:rate:";

    /**
     * 检查是否超出频率限制
     *
     * @return true=允许, false=超出限制
     */
    public boolean tryAcquire(Long userId) {
        String key = PREFIX + userId;
        Long count = redisUtil.increment(key);
        if (count == 1) {
            redisUtil.expire(key, 60, TimeUnit.SECONDS);
        }
        return count <= MAX_PER_MINUTE;
    }
}

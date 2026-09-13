package com.lingxi.user.agent;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 并发控制器
 * 限制：每用户同时只能有 1 个 Agent 会话在执行
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Component
public class AgentConcurrencyLimiter {

    private final ConcurrentHashMap<Long, AtomicBoolean> running = new ConcurrentHashMap<>();

    /**
     * 尝试获取执行权
     *
     * @return true=获取成功, false=已有任务在执行
     */
    public boolean tryAcquire(Long userId) {
        AtomicBoolean flag = running.computeIfAbsent(userId, k -> new AtomicBoolean(false));
        return flag.compareAndSet(false, true);
    }

    /**
     * 释放执行权
     */
    public void release(Long userId) {
        AtomicBoolean flag = running.get(userId);
        if (flag != null) {
            flag.set(false);
        }
    }
}

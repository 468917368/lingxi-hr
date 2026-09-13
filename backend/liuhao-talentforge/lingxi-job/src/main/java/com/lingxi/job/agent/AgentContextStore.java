package com.lingxi.job.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interview Agent 运行上下文存储
 * <p>
 * 单实例 P0 用 {@code ConcurrentHashMap}（阶段6 P1 迁移 Redis，见遗留问题）。
 * 提供 put/get/remove + 定时清理过期上下文。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
public class AgentContextStore {

    /** runToken → 运行上下文 */
    private final ConcurrentHashMap<String, AgentRunContext> contexts = new ConcurrentHashMap<>();

    /**
     * 写入运行上下文
     *
     * @param runToken 运行令牌
     * @param context  运行上下文
     */
    public void put(String runToken, AgentRunContext context) {
        contexts.put(runToken, context);
    }

    /**
     * 读取运行上下文
     *
     * @param runToken 运行令牌
     * @return 上下文（不存在返回 null）
     */
    public AgentRunContext get(String runToken) {
        return contexts.get(runToken);
    }

    /**
     * 移除运行上下文（正常结束/异常路径统一清理）
     *
     * @param runToken 运行令牌
     */
    public void remove(String runToken) {
        contexts.remove(runToken);
    }

    /**
     * 定时清理过期上下文（每 60s）
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();
        contexts.entrySet().removeIf(entry -> {
            AgentRunContext ctx = entry.getValue();
            return ctx == null || ctx.getExpiresAt() == null || ctx.getExpiresAt().isBefore(now);
        });
    }
}

package com.lingxi.chat.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket Session管理器
 * <p>
 * 管理用户ID与WebSocketSession的映射关系，采用本地内存+Redis双层存储：
 * - 本地内存（ConcurrentHashMap）：存储Session对象，用于直接发送消息
 * - Redis：存储在线状态（ws:online:{userId}），支持集群部署时跨节点判断在线
 * </p>
 * <p>
 * 核心功能：
 * - 单设备登录：同一用户新连接时踢掉旧连接
 * - 心跳续期：客户端每30秒PING，续期Redis在线状态（5分钟过期）
 * - 在线检查：优先查本地内存，再查Redis（集群兜底）
 * - 消息发送：通过Session直接发送，失败时自动清理
 * </p>
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** Redis在线状态Key前缀 */
    private static final String ONLINE_KEY_PREFIX = "ws:online:";

    /** 在线状态过期时间（秒），心跳每30秒续期一次 */
    private static final long ONLINE_EXPIRE_SECONDS = 300; // 5分钟

    /** 用户ID → Session映射（本地内存，线程安全） */
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 添加Session（单设备登录）
     * <p>
     * 如果该用户已有Session（旧连接），先关闭旧的再添加新的。
     * 同时在Redis中设置在线状态。
     * </p>
     *
     * @param userId  用户ID
     * @param session WebSocket Session
     */
    public void addSession(Long userId, WebSocketSession session) {
        WebSocketSession oldSession = sessions.get(userId);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.warn("关闭旧Session失败: userId={}", userId, e);
            }
        }
        sessions.put(userId, session);

        // 存储到Redis
        setUserOnline(userId);

        log.debug("用户上线: userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 移除Session
     *
     * @param userId 用户ID
     */
    public void removeSession(Long userId) {
        sessions.remove(userId);

        // 从Redis移除
        setUserOffline(userId);

        log.debug("用户下线: userId={}", userId);
    }

    /**
     * 设置用户在线状态到Redis
     *
     * @param userId 用户ID
     */
    private void setUserOnline(Long userId) {
        try {
            String key = ONLINE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, "1", ONLINE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.debug("Redis设置在线: userId={}", userId);
        } catch (Exception e) {
            log.error("Redis设置在线状态失败: userId={}", userId, e);
        }
    }

    /**
     * 设置用户离线状态从Redis移除
     *
     * @param userId 用户ID
     */
    private void setUserOffline(Long userId) {
        try {
            String key = ONLINE_KEY_PREFIX + userId;
            redisTemplate.delete(key);
            log.debug("Redis设置离线: userId={}", userId);
        } catch (Exception e) {
            log.error("Redis设置离线状态失败: userId={}", userId, e);
        }
    }

    /**
     * 续期在线状态
     *
     * @param userId 用户ID
     */
    public void refreshOnlineStatus(Long userId) {
        try {
            String key = ONLINE_KEY_PREFIX + userId;
            redisTemplate.expire(key, ONLINE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis续期在线状态失败: userId={}", userId, e);
        }
    }

    /**
     * 获取Session
     *
     * @param userId 用户ID
     * @return WebSocket Session，不存在返回null
     */
    public WebSocketSession getSession(Long userId) {
        return sessions.get(userId);
    }

    /**
     * 用户是否在线
     * <p>
     * 优先检查本地内存（快），再检查Redis（支持集群场景）。
     * 本地内存有Session且open → 在线
     * 本地没有但Redis有key → 在线（其他实例的连接）
     * 都没有 → 离线
     * </p>
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isOnline(Long userId) {
        // 先检查本地内存
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            return true;
        }

        // 再检查Redis（支持集群场景）
        try {
            String key = ONLINE_KEY_PREFIX + userId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis检查在线状态失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 获取所有在线用户ID
     *
     * @return 在线用户ID集合
     */
    public Set<Long> getOnlineUserIds() {
        return sessions.keySet();
    }

    /**
     * 获取在线用户数
     *
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return sessions.size();
    }

    /**
     * 向指定用户发送消息
     * <p>
     * 从本地内存获取Session，如果Session存在且open则发送消息。
     * 发送失败时自动移除Session（可能已断开）。
     * </p>
     *
     * @param userId  目标用户ID
     * @param message 消息内容（JSON字符串）
     */
    public void sendMessage(Long userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("发送消息失败: userId={}", userId, e);
                removeSession(userId);
            }
        }
    }

    /**
     * 向多个用户发送消息
     *
     * @param userIds 用户ID列表
     * @param message 消息内容
     */
    public void sendMessageToUsers(Set<Long> userIds, String message) {
        for (Long userId : userIds) {
            sendMessage(userId, message);
        }
    }
}

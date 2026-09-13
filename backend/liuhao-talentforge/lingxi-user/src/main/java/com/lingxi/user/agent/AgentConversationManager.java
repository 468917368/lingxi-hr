package com.lingxi.user.agent;

import com.lingxi.common.util.RedisUtil;
import com.lingxi.user.domain.entity.SysAgentConversation;
import com.lingxi.user.mapper.SysAgentConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Agent 对话历史管理
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConversationManager {

    private final SysAgentConversationMapper conversationMapper;
    private final RedisUtil redisUtil;

    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final long CACHE_TTL_HOURS = 24;
    private static final long SESSIONS_CACHE_TTL_MINUTES = 2;

    public void saveMessage(Long userId, String sessionId, String role, String content) {
        SysAgentConversation record = new SysAgentConversation();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setRole(role);
        record.setContent(content);
        conversationMapper.insert(record);
        redisUtil.delete(cacheKey(userId, sessionId));
        // 删除会话列表缓存
        clearSessionsCache(userId);
    }

    public List<SysAgentConversation> getHistory(Long userId, String sessionId) {
        return getHistory(userId, sessionId, DEFAULT_HISTORY_LIMIT);
    }

    public List<SysAgentConversation> getHistory(Long userId, String sessionId, int limit) {
        String key = cacheKey(userId, sessionId);
        List<SysAgentConversation> cached = redisUtil.get(key);
        if (cached != null) return cached;

        List<SysAgentConversation> history = conversationMapper.selectBySessionId(sessionId);
        if (history.size() > limit) {
            // 用 new ArrayList<> 包装，避免 subList() 返回的 ArrayList$SubList 无法序列化
            history = new ArrayList<>(history.subList(history.size() - limit, history.size()));
        }
        redisUtil.set(key, history, CACHE_TTL_HOURS, TimeUnit.HOURS);
        return history;
    }

    public String createSession(Long userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        saveMessage(userId, sessionId, "system", "会话创建");
        return sessionId;
    }

    public void deleteSession(Long userId, String sessionId) {
        conversationMapper.deleteBySessionId(sessionId);
        redisUtil.delete(cacheKey(userId, sessionId));
        // 删除会话列表缓存
        clearSessionsCache(userId);
    }

    public boolean isSessionOwner(Long userId, String sessionId) {
        Long ownerId = conversationMapper.selectUserIdBySessionId(sessionId);
        return ownerId != null && ownerId.equals(userId);
    }

    /**
     * 获取会话所有者ID（不存在返回null）
     */
    public Long getSessionOwnerId(String sessionId) {
        return conversationMapper.selectUserIdBySessionId(sessionId);
    }

    /**
     * 获取用户的会话列表（每个会话只返回最后一条消息作为摘要）
     */
    public List<SysAgentConversation> getUserSessions(Long userId, int page, int size) {
        String key = sessionsCacheKey(userId, page);

        // 检查缓存
        try {
            List<SysAgentConversation> cached = redisUtil.get(key);
            if (cached != null) {
                log.debug("会话列表缓存命中: userId={}, page={}", userId, page);
                return cached;
            }
        } catch (Exception e) {
            log.warn("读取会话列表缓存失败", e);
        }

        // 查数据库
        int offset = (page - 1) * size;
        List<SysAgentConversation> sessions = conversationMapper.selectUserSessions(userId, offset, size);

        // 写入缓存
        try {
            redisUtil.set(key, sessions, SESSIONS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入会话列表缓存失败", e);
        }

        return sessions;
    }

    /**
     * 统计用户的会话总数
     */
    public long countUserSessions(Long userId) {
        return conversationMapper.countUserSessions(userId);
    }

    /**
     * 清除用户的会话列表缓存
     */
    private void clearSessionsCache(Long userId) {
        try {
            // 清除第1页缓存（最常用）
            redisUtil.delete(sessionsCacheKey(userId, 1));
        } catch (Exception e) {
            log.warn("清除会话列表缓存失败", e);
        }
    }

    private String cacheKey(Long userId, String sessionId) {
        return String.format("agent:session:%d:%s", userId, sessionId);
    }

    private String sessionsCacheKey(Long userId, int page) {
        return String.format("agent:sessions:%d:%d", userId, page);
    }
}

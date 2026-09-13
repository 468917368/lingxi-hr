package com.lingxi.user.mapper;

import com.lingxi.user.domain.entity.SysAgentConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent对话历史Mapper
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Mapper
public interface SysAgentConversationMapper {

    /**
     * 插入对话记录
     *
     * @param conversation 对话实体
     * @return 影响行数
     */
    int insert(SysAgentConversation conversation);

    /**
     * 根据会话ID查询对话历史
     *
     * @param sessionId 会话ID
     * @return 对话列表
     */
    List<SysAgentConversation> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户的会话列表（按最后消息时间倒序）
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  限制数
     * @return 会话列表（每个会话只返回最后一条消息）
     */
    List<SysAgentConversation> selectUserSessions(@Param("userId") Long userId,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /**
     * 统计用户的会话总数
     *
     * @param userId 用户ID
     * @return 会话总数
     */
    long countUserSessions(@Param("userId") Long userId);

    /**
     * 查询会话的最后一条消息
     *
     * @param sessionId 会话ID
     * @return 最后一条消息
     */
    SysAgentConversation selectLastMessage(@Param("sessionId") String sessionId);

    /**
     * 统计会话的消息数量
     *
     * @param sessionId 会话ID
     * @return 消息数量
     */
    long countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 统计会话的Token消耗
     *
     * @param sessionId 会话ID
     * @return Token消耗总量
     */
    long sumTokensBySessionId(@Param("sessionId") String sessionId);

    /**
     * 删除会话的所有消息
     *
     * @param sessionId 会话ID
     * @return 影响行数
     */
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询会话的所有者用户ID（轻量查询）
     *
     * @param sessionId 会话ID
     * @return 用户ID，不存在返回null
     */
    Long selectUserIdBySessionId(@Param("sessionId") String sessionId);
}

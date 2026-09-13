package com.lingxi.chat.mapper;

import com.lingxi.chat.domain.entity.MsgMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息Mapper
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Mapper
public interface MsgMessageMapper {

    /**
     * 插入消息
     *
     * @param message 消息实体
     * @return 影响行数
     */
    int insert(MsgMessage message);

    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息实体
     */
    MsgMessage selectById(@Param("id") Long id);

    /**
     * 分页查询会话的消息列表（按时间倒序）
     *
     * @param conversationId 会话ID
     * @param offset         偏移量
     * @param limit          每页条数
     * @return 消息列表
     */
    List<MsgMessage> selectByConversationId(@Param("conversationId") Long conversationId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /**
     * 统计会话的消息总数
     *
     * @param conversationId 会话ID
     * @return 总数
     */
    long countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 标记消息为已读
     *
     * @param conversationId 会话ID
     * @param userId         接收者ID
     * @return 影响行数
     */
    int markAsRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * 查询用户的未读消息列表
     * 条件：用户参与的会话中，非自己发送的、未读的消息
     *
     * @param userId 用户ID
     * @return 未读消息列表
     */
    List<MsgMessage> selectUnreadByUserId(@Param("userId") Long userId);

    /**
     * 撤回消息（更新内容为系统提示，contentType 改为 SYSTEM）
     *
     * @param messageId 消息ID
     * @param senderId  发送者ID（校验权限）
     * @return 影响行数（0=消息不存在或非本人发送）
     */
    int recallMessage(@Param("messageId") Long messageId, @Param("senderId") Long senderId);

    /**
     * 增量查询新消息（用于轮询）
     *
     * @param conversationId 会话ID
     * @param sinceId        只返回此ID之后的消息
     * @return 新消息列表（按时间正序）
     */
    List<MsgMessage> selectAfterId(@Param("conversationId") Long conversationId,
                                   @Param("sinceId") Long sinceId);
}

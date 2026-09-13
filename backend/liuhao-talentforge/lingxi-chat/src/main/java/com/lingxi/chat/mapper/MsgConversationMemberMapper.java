package com.lingxi.chat.mapper;

import com.lingxi.chat.domain.entity.MsgConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话成员Mapper
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Mapper
public interface MsgConversationMemberMapper {

    /**
     * 插入会话成员
     *
     * @param member 会话成员实体
     * @return 影响行数
     */
    int insert(MsgConversationMember member);

    /**
     * 查询会话的所有成员
     *
     * @param conversationId 会话ID
     * @return 成员列表
     */
    List<MsgConversationMember> selectByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 查询用户在指定会话中的成员信息
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 成员实体
     */
    MsgConversationMember selectByConversationAndUser(@Param("conversationId") Long conversationId,
                                                       @Param("userId") Long userId);

    /**
     * 查询用户参与的所有会话成员记录
     *
     * @param userId 用户ID
     * @return 成员列表
     */
    List<MsgConversationMember> selectByUserId(@Param("userId") Long userId);

    /**
     * 批量查询会话的成员列表
     *
     * @param conversationIds 会话ID列表
     * @return 成员列表
     */
    List<MsgConversationMember> selectByConversationIds(@Param("conversationIds") List<Long> conversationIds);

    /**
     * 更新未读数
     *
     * @param id          成员ID
     * @param unreadCount 未读数
     * @return 影响行数
     */
    int updateUnreadCount(@Param("id") Long id, @Param("unreadCount") int unreadCount);

    /**
     * 增加未读数
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 影响行数
     */
    int incrementUnreadCount(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId);

    /**
     * 清零未读数
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 影响行数
     */
    int clearUnreadCount(@Param("conversationId") Long conversationId,
                         @Param("userId") Long userId);

    /**
     * 标记会话删除（仅对自己隐藏）
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 影响行数
     */
    int markDeleted(@Param("conversationId") Long conversationId,
                    @Param("userId") Long userId);

    /**
     * 更新置顶状态
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param isTop          是否置顶：0=否 1=是
     * @return 影响行数
     */
    int updateIsTop(@Param("conversationId") Long conversationId,
                    @Param("userId") Long userId,
                    @Param("isTop") int isTop);

    /**
     * 更新免打扰状态
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param isMuted        是否免打扰：0=否 1=是
     * @return 影响行数
     */
    int updateIsMuted(@Param("conversationId") Long conversationId,
                      @Param("userId") Long userId,
                      @Param("isMuted") int isMuted);
}

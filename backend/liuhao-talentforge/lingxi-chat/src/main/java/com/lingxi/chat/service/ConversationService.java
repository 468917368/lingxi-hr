package com.lingxi.chat.service;

import com.lingxi.chat.domain.dto.CreateConversationRequest;
import com.lingxi.chat.domain.entity.MsgConversationMember;
import com.lingxi.chat.domain.vo.ConversationVO;
import com.lingxi.chat.domain.vo.MessageVO;
import com.lingxi.common.domain.PageResult;

import java.util.List;

/**
 * 会话服务接口
 *
 * @author 成员A
 * @since 2026-08-03
 */
public interface ConversationService {

    /**
     * 创建会话
     *
     * @param userId  当前用户ID
     * @param request 创建请求
     * @return 会话ID
     */
    Long createConversation(Long userId, CreateConversationRequest request);

    /**
     * 获取会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ConversationVO> getConversations(Long userId);

    /**
     * 获取会话详情
     *
     * @param userId       当前用户ID
     * @param conversationId 会话ID
     * @return 会话详情
     */
    ConversationVO getConversationDetail(Long userId, Long conversationId);

    /**
     * 获取聊天记录（分页）
     *
     * @param userId       当前用户ID
     * @param conversationId 会话ID
     * @param page         页码
     * @param size         每页条数
     * @return 消息列表
     */
    PageResult<MessageVO> getMessages(Long userId, Long conversationId, int page, int size);

    /**
     * 增量获取新消息（用于轮询）
     *
     * @param userId       当前用户ID
     * @param conversationId 会话ID
     * @param sinceId      只返回此ID之后的消息
     * @return 新消息列表
     */
    List<MessageVO> getMessagesAfterId(Long userId, Long conversationId, Long sinceId);

    /**
     * 发送消息
     *
     * @param userId       发送者ID
     * @param conversationId 会话ID
     * @param msgType      消息类型：TEXT/RICH_TEXT/IMAGE/FILE
     * @param contentType  业务消息类型
     * @param content      消息内容
     * @param mediaUrl     媒体文件URL
     * @param fileName     文件名
     * @param fileSize     文件大小
     * @return 消息ID
     */
    Long sendMessage(Long userId, Long conversationId, String msgType, String contentType, String content,
                     String mediaUrl, String fileName, Long fileSize);

    /**
     * 删除会话（仅对自己隐藏）
     *
     * @param userId       用户ID
     * @param conversationId 会话ID
     */
    void deleteConversation(Long userId, Long conversationId);

    /**
     * 标记会话已读
     *
     * @param userId       用户ID
     * @param conversationId 会话ID
     */
    void markAsRead(Long userId, Long conversationId);

    /**
     * 设置会话置顶
     *
     * @param userId       用户ID
     * @param conversationId 会话ID
     * @param isTop        是否置顶
     */
    void setTop(Long userId, Long conversationId, boolean isTop);

    /**
     * 设置会话免打扰
     *
     * @param userId       用户ID
     * @param conversationId 会话ID
     * @param isMuted      是否免打扰
     */
    void setMuted(Long userId, Long conversationId, boolean isMuted);

    /**
     * 获取用户未读消息总数
     *
     * @param userId 用户ID
     * @return 未读消息数
     */
    int getUnreadCount(Long userId);

    /**
     * 获取会话成员ID列表
     *
     * @param conversationId 会话ID
     * @return 成员ID列表
     */
    List<Long> getConversationMemberIds(Long conversationId);

    /**
     * 查询用户在指定会话中的成员信息
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 成员实体，不存在返回 null
     */
    MsgConversationMember getConversationMember(Long conversationId, Long userId);
}

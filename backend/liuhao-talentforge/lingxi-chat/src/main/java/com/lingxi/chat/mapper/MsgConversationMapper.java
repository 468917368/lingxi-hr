package com.lingxi.chat.mapper;

import com.lingxi.chat.domain.entity.MsgConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话Mapper
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Mapper
public interface MsgConversationMapper {

    /**
     * 插入会话
     *
     * @param conversation 会话实体
     * @return 影响行数
     */
    int insert(MsgConversation conversation);

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 会话实体
     */
    MsgConversation selectById(@Param("id") Long id);

    /**
     * 查询用户参与的会话列表（按最后消息时间倒序）
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<MsgConversation> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据业务条件查询会话
     *
     * @param companyId    企业ID
     * @param candidateId  求职者ID
     * @param hrId         HR ID
     * @return 会话实体
     */
    MsgConversation selectByBusiness(@Param("companyId") Long companyId,
                                     @Param("candidateId") Long candidateId,
                                     @Param("hrId") Long hrId);

    /**
     * 更新会话最后消息信息
     *
     * @param id                 会话ID
     * @param lastMessagePreview 最后消息摘要
     * @return 影响行数
     */
    int updateLastMsg(@Param("id") Long id,
                      @Param("lastMessagePreview") String lastMessagePreview);

    /**
     * 增加求职者未读数
     *
     * @param id 会话ID
     * @return 影响行数
     */
    int incrementCandidateUnread(@Param("id") Long id);

    /**
     * 增加HR未读数
     *
     * @param id 会话ID
     * @return 影响行数
     */
    int incrementHrUnread(@Param("id") Long id);

    /**
     * 更新最后消息并增加求职者未读数（合并操作，减少数据库查询）
     *
     * @param id                 会话ID
     * @param lastMessagePreview 最后消息摘要
     * @return 影响行数
     */
    int updateLastMsgAndIncrementCandidateUnread(@Param("id") Long id,
                                                  @Param("lastMessagePreview") String lastMessagePreview);

    /**
     * 更新最后消息并增加HR未读数（合并操作，减少数据库查询）
     *
     * @param id                 会话ID
     * @param lastMessagePreview 最后消息摘要
     * @return 影响行数
     */
    int updateLastMsgAndIncrementHrUnread(@Param("id") Long id,
                                          @Param("lastMessagePreview") String lastMessagePreview);

    /**
     * 清零求职者未读数
     *
     * @param id 会话ID
     * @return 影响行数
     */
    int clearCandidateUnread(@Param("id") Long id);

    /**
     * 清零HR未读数
     *
     * @param id 会话ID
     * @return 影响行数
     */
    int clearHrUnread(@Param("id") Long id);

    /**
     * 统计用户未读消息总数（SQL求和）
     *
     * @param userId 用户ID
     * @return 未读总数
     */
    Integer sumUnreadByUserId(@Param("userId") Long userId);

    /**
     * 清零用户未读数（自动判断角色）
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 影响行数
     */
    int clearUnreadByUserId(@Param("conversationId") Long conversationId,
                            @Param("userId") Long userId);
}

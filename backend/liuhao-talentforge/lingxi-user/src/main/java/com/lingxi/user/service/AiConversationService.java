package com.lingxi.user.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.user.domain.vo.AiSessionVO;

import java.util.List;

/**
 * AI会话服务接口
 *
 * @author 成员A
 * @since 2026-08-03
 */
public interface AiConversationService {

    /**
     * 获取用户的AI会话列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页条数
     * @return 会话列表
     */
    PageResult<AiSessionVO> getSessions(Long userId, int page, int size);

    /**
     * 获取会话详情
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 会话详情
     */
    AiSessionVO getSessionDetail(Long userId, String sessionId);

    /**
     * 删除会话
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     */
    void deleteSession(Long userId, String sessionId);

    /**
     * 创建新会话
     *
     * @param userId 用户ID
     * @return 会话ID
     */
    String createSession(Long userId);
}

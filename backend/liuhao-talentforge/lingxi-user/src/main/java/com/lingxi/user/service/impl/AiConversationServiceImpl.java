package com.lingxi.user.service.impl;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.user.domain.entity.SysAgentConversation;
import com.lingxi.user.domain.vo.AiSessionVO;
import com.lingxi.user.mapper.SysAgentConversationMapper;
import com.lingxi.user.service.AiConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI会话服务实现
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl implements AiConversationService {

    private final SysAgentConversationMapper agentConversationMapper;

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<AiSessionVO> getSessions(Long userId, int page, int size) {
        // 计算偏移量
        int offset = (page - 1) * size;

        // 查询会话列表
        List<SysAgentConversation> sessions = agentConversationMapper.selectUserSessions(userId, offset, size);
        long total = agentConversationMapper.countUserSessions(userId);

        // 组装返回
        List<AiSessionVO> voList = new ArrayList<>();
        for (SysAgentConversation session : sessions) {
            // 获取会话详情
            long messageCount = agentConversationMapper.countBySessionId(session.getSessionId());
            long totalTokens = agentConversationMapper.sumTokensBySessionId(session.getSessionId());

            // 截取消息内容作为标题
            String title = truncateContent(session.getContent(), 20);
            String lastMessage = truncateContent(session.getContent(), 50);

            AiSessionVO vo = AiSessionVO.builder()
                    .sessionId(session.getSessionId())
                    .title(title)
                    .lastMessage(lastMessage)
                    .lastMessageTime(session.getCreatedAt() != null ? session.getCreatedAt().format(DATE_FORMATTER) : null)
                    .messageCount(messageCount)
                    .totalTokens(totalTokens)
                    .build();

            voList.add(vo);
        }

        return PageResult.of(voList, total, page, size);
    }

    @Override
    public AiSessionVO getSessionDetail(Long userId, String sessionId) {
        // 查询会话的最后一条消息
        SysAgentConversation lastMessage = agentConversationMapper.selectLastMessage(sessionId);
        if (lastMessage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 校验用户权限
        if (!lastMessage.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 获取会话详情
        long messageCount = agentConversationMapper.countBySessionId(sessionId);
        long totalTokens = agentConversationMapper.sumTokensBySessionId(sessionId);

        // 查询第一条用户消息作为标题
        List<SysAgentConversation> messages = agentConversationMapper.selectBySessionId(sessionId);
        String title = "新会话";
        for (SysAgentConversation msg : messages) {
            if ("user".equals(msg.getRole())) {
                title = truncateContent(msg.getContent(), 20);
                break;
            }
        }

        return AiSessionVO.builder()
                .sessionId(sessionId)
                .title(title)
                .lastMessage(truncateContent(lastMessage.getContent(), 50))
                .lastMessageTime(lastMessage.getCreatedAt() != null ? lastMessage.getCreatedAt().format(DATE_FORMATTER) : null)
                .messageCount(messageCount)
                .totalTokens(totalTokens)
                .build();
    }

    @Override
    public void deleteSession(Long userId, String sessionId) {
        // 查询会话的最后一条消息
        SysAgentConversation lastMessage = agentConversationMapper.selectLastMessage(sessionId);
        if (lastMessage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 校验用户权限
        if (!lastMessage.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 删除会话
        agentConversationMapper.deleteBySessionId(sessionId);
        log.info("删除AI会话成功: userId={}, sessionId={}", userId, sessionId);
    }

    @Override
    public String createSession(Long userId) {
        // 生成会话ID
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        // 插入系统消息
        SysAgentConversation conversation = new SysAgentConversation();
        conversation.setSessionId(sessionId);
        conversation.setUserId(userId);
        conversation.setRole("system");
        conversation.setContent("你好！我是Job Agent，可以帮你解答求职相关的问题。请问有什么可以帮到你的？");
        agentConversationMapper.insert(conversation);

        log.info("创建AI会话成功: userId={}, sessionId={}", userId, sessionId);
        return sessionId;
    }

    /**
     * 截取内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}

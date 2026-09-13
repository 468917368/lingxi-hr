package com.lingxi.chat.service.impl;

import com.lingxi.chat.constant.ChatConstant;
import com.lingxi.chat.domain.dto.CreateConversationRequest;
import com.lingxi.chat.domain.entity.MsgConversation;
import com.lingxi.chat.domain.entity.MsgConversationMember;
import com.lingxi.chat.domain.entity.MsgMessage;
import com.lingxi.chat.domain.vo.ConversationVO;
import com.lingxi.chat.domain.vo.MessageVO;
import com.lingxi.chat.feign.UserFeignClient;
import com.lingxi.chat.mapper.MsgConversationMapper;
import com.lingxi.chat.mapper.MsgConversationMemberMapper;
import com.lingxi.chat.mapper.MsgMessageMapper;
import com.lingxi.chat.service.ConversationService;
import com.lingxi.chat.websocket.WebSocketSessionManager;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话服务实现
 * <p>
 * 负责会话CRUD、消息收发、已读标记、置顶/免打扰等核心功能。
 * 会话表(msg_conversation)按candidateId+hrId+companyId唯一标识，
 * 消息表(msg_message)存储所有聊天记录，通过WebSocket实时推送。
 * </p>
 * <p>
 * 缓存策略：
 * - 会话列表：@Cacheable("conversations", key=userId)，发消息/删除时清除
 * - 未读数：@Cacheable("unreadCount", key=userId)，已读/发消息时清除
 * </p>
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final MsgConversationMapper conversationMapper;
    private final MsgConversationMemberMapper memberMapper;
    private final MsgMessageMapper messageMapper;
    private final UserFeignClient userFeignClient;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 创建会话
     * <p>
     * 按(companyId, candidateId, hrId)去重，已存在则返回已有会话ID。
     * 同时创建两条成员记录（求职者+HR），初始未读数为0。
     * </p>
     *
     * @param userId  当前用户ID（HR或求职者）
     * @param request 会话创建请求（含企业ID、候选人ID、HR ID、投递ID）
     * @return 会话ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(Long userId, CreateConversationRequest request) {
        // 1. 检查会话是否已存在（防止重复创建）
        MsgConversation existing = conversationMapper.selectByBusiness(
                request.getCompanyId(), request.getCandidateId(), request.getHrId());
        if (existing != null) {
            return existing.getId();
        }

        // 2. 创建会话
        MsgConversation conversation = new MsgConversation();
        conversation.setCompanyId(request.getCompanyId());
        conversation.setCandidateId(request.getCandidateId());
        conversation.setHrId(request.getHrId());
        conversation.setApplicationId(request.getApplicationId());
        conversation.setCandidateUnread(0);
        conversation.setHrUnread(0);
        conversationMapper.insert(conversation);

        // 3. 添加成员
        addMember(conversation.getId(), request.getCandidateId(), ChatConstant.MEMBER_ROLE_CANDIDATE);
        addMember(conversation.getId(), request.getHrId(), ChatConstant.MEMBER_ROLE_HR);

        log.info("创建会话成功: conversationId={}, companyId={}", conversation.getId(), request.getCompanyId());
        return conversation.getId();
    }

    /**
     * 获取用户的会话列表
     * <p>
     * 查询用户参与的所有会话，批量获取对方用户信息（Feign），
     * 按最后消息时间倒序排列。结果缓存10分钟。
     * </p>
     *
     * @param userId 当前用户ID
     * @return 会话列表（含对方头像/姓名、最后消息、未读数）
     */
    @Override
    @Cacheable(value = "conversations", key = "#userId")
    public List<ConversationVO> getConversations(Long userId) {
        // 1. 查询用户参与的会话
        List<MsgConversation> conversations = conversationMapper.selectByUserId(userId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 收集对方用户ID
        Set<Long> targetUserIds = new HashSet<>();
        for (MsgConversation conv : conversations) {
            Long targetUserId = userId.equals(conv.getCandidateId()) ? conv.getHrId() : conv.getCandidateId();
            targetUserIds.add(targetUserId);
        }

        // 3. 批量查询用户信息
        Map<Long, Map<String, Object>> userMap = new HashMap<>();
        if (!targetUserIds.isEmpty()) {
            try {
                List<Map<String, Object>> users = userFeignClient.getUsersByIds(new ArrayList<>(targetUserIds)).getData();
                for (Map<String, Object> user : users) {
                    Long id = Long.valueOf(user.get("id").toString());
                    userMap.put(id, user);
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败", e);
            }
        }

        // 4. 组装返回
        List<ConversationVO> result = new ArrayList<>();
        for (MsgConversation conv : conversations) {
            // 获取对方用户ID
            Long targetUserId = userId.equals(conv.getCandidateId()) ? conv.getHrId() : conv.getCandidateId();

            // 计算未读数
            int unreadCount = userId.equals(conv.getCandidateId())
                    ? (conv.getCandidateUnread() != null ? conv.getCandidateUnread() : 0)
                    : (conv.getHrUnread() != null ? conv.getHrUnread() : 0);

            ConversationVO vo = ConversationVO.builder()
                    .id(conv.getId())
                    .targetUser(buildTargetUser(targetUserId, userMap))
                    .lastMessage(conv.getLastMessagePreview())
                    .lastMessageTime(conv.getLastMessageAt() != null ? conv.getLastMessageAt().format(DATE_FORMATTER) : null)
                    .unreadCount(unreadCount)
                    .build();

            result.add(vo);
        }

        // 按最后消息时间排序
        result.sort((a, b) -> {
            if (a.getLastMessageTime() == null) return 1;
            if (b.getLastMessageTime() == null) return -1;
            return b.getLastMessageTime().compareTo(a.getLastMessageTime());
        });

        return result;
    }

    /**
     * 获取会话详情
     * <p>
     * 校验用户是否为会话成员后，返回会话详情含对方用户信息和未读数。
     * </p>
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @return 会话详情
     * @throws BusinessException 404 会话不存在 / 403 非会话成员
     */
    @Override
    public ConversationVO getConversationDetail(Long userId, Long conversationId) {
        // 1. 查询会话
        MsgConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 2. 校验用户是否是会话成员
        if (!userId.equals(conversation.getCandidateId()) && !userId.equals(conversation.getHrId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 3. 获取对方用户信息
        Long targetUserId = userId.equals(conversation.getCandidateId()) ? conversation.getHrId() : conversation.getCandidateId();
        ConversationVO.TargetUserVO targetUser = null;
        try {
            Map<String, Object> user = userFeignClient.getUserById(targetUserId).getData();
            if (user != null) {
                targetUser = ConversationVO.TargetUserVO.builder()
                        .id(targetUserId)
                        .name((String) user.get("name"))
                        .avatar((String) user.get("avatar"))
                        .role((String) user.get("role"))
                        .company((String) user.get("companyName"))
                        .build();
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}", targetUserId, e);
            targetUser = ConversationVO.TargetUserVO.builder()
                    .id(targetUserId)
                    .name("用户")
                    .avatar("/default-avatar.png")
                    .build();
        }

        // 4. 计算未读数
        int unreadCount = userId.equals(conversation.getCandidateId())
                ? (conversation.getCandidateUnread() != null ? conversation.getCandidateUnread() : 0)
                : (conversation.getHrUnread() != null ? conversation.getHrUnread() : 0);

        // 5. 组装返回
        return ConversationVO.builder()
                .id(conversation.getId())
                .targetUser(targetUser)
                .lastMessage(conversation.getLastMessagePreview())
                .lastMessageTime(conversation.getLastMessageAt() != null ? conversation.getLastMessageAt().format(DATE_FORMATTER) : null)
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 获取聊天记录（分页）
     * <p>
     * 校验用户是否为会话成员后，分页查询消息列表。
     * 批量获取发送者信息（Feign），计算消息状态（SENT/DELIVERED/READ）。
     * </p>
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @param page           页码（从1开始）
     * @param size           每页条数
     * @return 分页消息列表
     * @throws BusinessException 403 非会话成员
     */
    @Override
    public PageResult<MessageVO> getMessages(Long userId, Long conversationId, int page, int size) {
        // 1. 校验用户是否是会话成员
        MsgConversationMember member = memberMapper.selectByConversationAndUser(conversationId, userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 2. 查询消息
        int offset = (page - 1) * size;
        List<MsgMessage> messages = messageMapper.selectByConversationId(conversationId, offset, size);
        long total = messageMapper.countByConversationId(conversationId);

        // 3. 收集发送者ID
        Set<Long> senderIds = messages.stream()
                .map(MsgMessage::getSenderId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());

        // 4. 查询发送者信息
        Map<Long, Map<String, Object>> userMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            try {
                List<Map<String, Object>> users = userFeignClient.getUsersByIds(new ArrayList<>(senderIds)).getData();
                for (Map<String, Object> user : users) {
                    Long id = Long.valueOf(user.get("id").toString());
                    userMap.put(id, user);
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败", e);
            }
        }

        // 5. 组装返回
        List<MessageVO> voList = messages.stream()
                .map(m -> convertToMessageVO(m, userMap, userId))
                .collect(Collectors.toList());

        return PageResult.of(voList, total, page, size);
    }

    /**
     * 增量获取新消息
     * <p>
     * 返回sinceId之后的新消息，用于前端轮询或WebSocket断连后补拉。
     * </p>
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @param sinceId        起始消息ID（不含）
     * @return 新消息列表
     */
    @Override
    public List<MessageVO> getMessagesAfterId(Long userId, Long conversationId, Long sinceId) {
        // 1. 校验用户是否是会话成员
        MsgConversationMember member = memberMapper.selectByConversationAndUser(conversationId, userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 2. 查询新消息
        List<MsgMessage> messages = messageMapper.selectAfterId(conversationId, sinceId);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 收集发送者ID
        Set<Long> senderIds = messages.stream()
                .map(MsgMessage::getSenderId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());

        // 4. 查询发送者信息
        Map<Long, Map<String, Object>> userMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            try {
                List<Map<String, Object>> users = userFeignClient.getUsersByIds(new ArrayList<>(senderIds)).getData();
                for (Map<String, Object> user : users) {
                    Long id = Long.valueOf(user.get("id").toString());
                    userMap.put(id, user);
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败", e);
            }
        }

        // 5. 组装返回
        return messages.stream()
                .map(m -> convertToMessageVO(m, userMap, userId))
                .collect(Collectors.toList());
    }

    /**
     * 发送消息
     * <p>
     * 校验会话权限后，保存消息到数据库，更新会话最后消息和对方未读数，
     * 异步推送WebSocket通知给对方（如果在线）。
     * 清除发送方的会话缓存和接收方的未读数缓存。
     * </p>
     *
     * @param userId         发送者ID
     * @param conversationId 会话ID
     * @param msgType        消息类型：TEXT/RICH_TEXT/IMAGE/FILE
     * @param contentType    业务类型：TEXT/IMAGE/FILE/SYSTEM/CARD_RESUME/CARD_JOB
     * @param content        消息内容
     * @param mediaUrl       媒体文件URL（图片/文件消息时有值）
     * @param fileName       文件名
     * @param fileSize       文件大小（字节）
     * @return 消息ID
     * @throws BusinessException 403 非会话成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "conversations", key = "#userId")
    public Long sendMessage(Long userId, Long conversationId, String msgType, String contentType, String content,
                            String mediaUrl, String fileName, Long fileSize) {
        // 1. 校验会话权限（一次查询获取成员信息）
        MsgConversationMember member = memberMapper.selectByConversationAndUser(conversationId, userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 2. 保存消息
        MsgMessage message = new MsgMessage();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setSenderRole(member.getMemberRole());
        message.setMsgType(msgType != null ? msgType : ChatConstant.MSG_TYPE_TEXT);
        message.setContentType(contentType);
        message.setContent(content);
        message.setMediaUrl(mediaUrl);
        message.setFileName(fileName);
        message.setFileSize(fileSize);
        message.setIsRead(0);
        message.setCreatedAt(java.time.LocalDateTime.now());
        messageMapper.insert(message);

        // 3. 更新会话最后消息和未读数（合并为一次更新）
        String lastMessagePreview = ChatConstant.CONTENT_TYPE_TEXT.equals(contentType)
                ? (content.length() > 50 ? content.substring(0, 50) : content) : "[" + contentType + "]";
        boolean isCandidate = ChatConstant.MEMBER_ROLE_CANDIDATE.equals(member.getMemberRole());

        // 获取对方用户ID，用于清除对方的未读数缓存
        Long receiverId;
        if (isCandidate) {
            conversationMapper.updateLastMsgAndIncrementHrUnread(conversationId, lastMessagePreview);
            receiverId = getHrId(conversationId);
        } else {
            conversationMapper.updateLastMsgAndIncrementCandidateUnread(conversationId, lastMessagePreview);
            receiverId = getCandidateId(conversationId);
        }

        // 清除对方的未读数缓存
        if (receiverId != null) {
            evictUnreadCountCache(receiverId);
        }

        // 4. 异步推送消息给对方（如果在线）
        Long finalMessageId = message.getId();
        asyncPushMessage(userId, conversationId, message);

        log.info("消息发送成功: messageId={}, conversationId={}", finalMessageId, conversationId);
        return finalMessageId;
    }

    /**
     * 异步推送消息给对方
     * <p>
     * 从会话表获取对方ID，如果对方在线则通过WebSocket推送NEW_MESSAGE事件。
     * </p>
     *
     * @param senderId       发送者ID
     * @param conversationId 会话ID
     * @param message        消息实体
     */
    private void asyncPushMessage(Long senderId, Long conversationId, MsgMessage message) {
        try {
            // 直接从会话表获取对方ID，避免查询成员表
            MsgConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null) return;

            Long targetUserId = senderId.equals(conversation.getCandidateId())
                    ? conversation.getHrId()
                    : conversation.getCandidateId();

            if (targetUserId != null && sessionManager.isOnline(targetUserId)) {
                pushNewMessage(targetUserId, message, senderId);
            }
        } catch (Exception e) {
            log.error("异步推送消息失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 删除会话（仅对自己隐藏）
     * <p>
     * 标记会话成员的is_deleted=1，不影响对方。
     * 清除用户的会话列表缓存。
     * </p>
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "conversations", key = "#userId")
    public void deleteConversation(Long userId, Long conversationId) {
        memberMapper.markDeleted(conversationId, userId);
        log.info("会话删除成功: userId={}, conversationId={}", userId, conversationId);
    }

    /**
     * 标记会话已读
     * <p>
     * 清零成员表未读数、标记消息已读、清零会话表未读数。
     * 清除用户的未读数缓存。
     * </p>
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     */
    @Override
    @CacheEvict(value = "unreadCount", key = "#userId")
    public void markAsRead(Long userId, Long conversationId) {
        memberMapper.clearUnreadCount(conversationId, userId);
        messageMapper.markAsRead(conversationId, userId);

        // 清零会话表中的未读数（合并SQL，无需查询会话）
        conversationMapper.clearUnreadByUserId(conversationId, userId);
    }

    /**
     * 设置会话置顶/取消置顶
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @param isTop          true=置顶, false=取消置顶
     * @throws BusinessException 403 非会话成员
     */
    @Override
    public void setTop(Long userId, Long conversationId, boolean isTop) {
        // 校验用户是否是会话成员
        MsgConversationMember member = memberMapper.selectByConversationAndUser(conversationId, userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        memberMapper.updateIsTop(conversationId, userId, isTop ? 1 : 0);
        log.info("设置会话置顶: userId={}, conversationId={}, isTop={}", userId, conversationId, isTop);
    }

    /**
     * 设置会话免打扰/取消免打扰
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @param isMuted        true=免打扰, false=取消免打扰
     * @throws BusinessException 403 非会话成员
     */
    @Override
    public void setMuted(Long userId, Long conversationId, boolean isMuted) {
        // 校验用户是否是会话成员
        MsgConversationMember member = memberMapper.selectByConversationAndUser(conversationId, userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        memberMapper.updateIsMuted(conversationId, userId, isMuted ? 1 : 0);
        log.info("设置会话免打扰: userId={}, conversationId={}, isMuted={}", userId, conversationId, isMuted);
    }

    /**
     * 获取用户未读消息总数
     * <p>
     * SQL直接求和，避免加载所有会话到内存。结果缓存。
     * </p>
     *
     * @param userId 用户ID
     * @return 未读消息数
     */
    @Override
    @Cacheable(value = "unreadCount", key = "#userId")
    public int getUnreadCount(Long userId) {
        // SQL 直接求和，避免加载所有会话到内存
        Integer total = conversationMapper.sumUnreadByUserId(userId);
        return total != null ? total : 0;
    }

    /**
     * 获取会话成员ID列表
     *
     * @param conversationId 会话ID
     * @return 成员用户ID列表
     */
    @Override
    public List<Long> getConversationMemberIds(Long conversationId) {
        List<MsgConversationMember> members = memberMapper.selectByConversationId(conversationId);
        return members.stream()
                .map(MsgConversationMember::getUserId)
                .collect(Collectors.toList());
    }

    /**
     * 查询用户在指定会话中的成员信息
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 成员实体，不存在返回null
     */
    @Override
    public MsgConversationMember getConversationMember(Long conversationId, Long userId) {
        return memberMapper.selectByConversationAndUser(conversationId, userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 添加会话成员
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param role           角色：CANDIDATE/HR
     */
    private void addMember(Long conversationId, Long userId, String role) {
        MsgConversationMember member = new MsgConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setUnreadCount(0);
        member.setIsTop(0);
        member.setIsMuted(0);
        member.setIsDeleted(0);
        memberMapper.insert(member);
    }

    /**
     * 获取会话中的HR用户ID
     */
    private Long getHrId(Long conversationId) {
        MsgConversation conversation = conversationMapper.selectById(conversationId);
        return conversation != null ? conversation.getHrId() : null;
    }

    /**
     * 获取会话中的求职者用户ID
     */
    private Long getCandidateId(Long conversationId) {
        MsgConversation conversation = conversationMapper.selectById(conversationId);
        return conversation != null ? conversation.getCandidateId() : null;
    }

    /**
     * 清除用户的未读数缓存
     */
    private void evictUnreadCountCache(Long userId) {
        try {
            // 使用 Spring CacheManager 清除缓存
            org.springframework.cache.Cache cache = cacheManager.getCache("unreadCount");
            if (cache != null) {
                cache.evict(userId);
            }
        } catch (Exception e) {
            log.warn("清除未读数缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 构建对方用户信息VO
     *
     * @param userId  对方用户ID
     * @param userMap 用户信息Map（批量查询结果）
     * @return 用户信息VO，查询失败时返回默认值
     */
    private ConversationVO.TargetUserVO buildTargetUser(Long userId, Map<Long, Map<String, Object>> userMap) {
        if (userId == null) return null;
        Map<String, Object> user = userMap.get(userId);
        if (user == null) {
            return ConversationVO.TargetUserVO.builder()
                    .id(userId)
                    .name("用户")
                    .avatar("/default-avatar.png")
                    .isOnline(sessionManager.isOnline(userId))
                    .build();
        }
        return ConversationVO.TargetUserVO.builder()
                .id(userId)
                .name((String) user.get("name"))
                .avatar((String) user.get("avatar"))
                .role((String) user.get("role"))
                .company((String) user.get("companyName"))
                .isOnline(sessionManager.isOnline(userId))
                .build();
    }

    /**
     * 将消息实体转换为VO
     *
     * @param message      消息实体
     * @param userMap      发送者信息Map
     * @param currentUserId 当前用户ID（用于计算消息状态）
     * @return 消息VO
     */
    private MessageVO convertToMessageVO(MsgMessage message, Map<Long, Map<String, Object>> userMap, Long currentUserId) {
        Map<String, Object> sender = message.getSenderId() != 0 ? userMap.get(message.getSenderId()) : null;

        // 计算消息状态
        String status;
        if (message.getIsRead() == 1) {
            status = "READ"; // 已读
        } else if (message.getSenderId().equals(currentUserId)) {
            status = "SENT"; // 自己发送的，已送达
        } else {
            status = "DELIVERED"; // 对方发送的，已送达
        }

        return MessageVO.builder()
                .id(message.getId())
                .senderId(message.getSenderId())
                .senderName(sender != null ? (String) sender.get("name") : "系统")
                .senderAvatar(sender != null ? (String) sender.get("avatar") : null)
                .msgType(message.getMsgType())
                .contentType(message.getContentType())
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .isRead(message.getIsRead() == 1)
                .status(status)
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().format(DATE_FORMATTER) : null)
                .build();
    }

    /**
     * 通过WebSocket推送新消息给指定用户
     *
     * @param userId    目标用户ID
     * @param message   消息实体
     * @param senderId  发送者ID
     */
    private void pushNewMessage(Long userId, MsgMessage message, Long senderId) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", ChatConstant.NEW_MESSAGE);
            Map<String, Object> data = new HashMap<>();
            data.put("messageId", message.getId());
            data.put("conversationId", message.getConversationId());
            data.put("senderId", senderId);
            data.put("msgType", message.getMsgType());
            data.put("contentType", message.getContentType());
            data.put("content", message.getContent());
            data.put("mediaUrl", message.getMediaUrl());
            data.put("fileName", message.getFileName());
            data.put("fileSize", message.getFileSize());
            data.put("createdAt", message.getCreatedAt().format(DATE_FORMATTER));
            msg.put("data", data);
            sessionManager.sendMessage(userId, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("推送消息失败: userId={}", userId, e);
        }
    }
}

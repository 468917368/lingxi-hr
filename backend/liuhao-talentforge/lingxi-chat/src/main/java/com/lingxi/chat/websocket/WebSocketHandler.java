package com.lingxi.chat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.chat.constant.ChatConstant;
import com.lingxi.chat.domain.entity.MsgConversationMember;
import com.lingxi.chat.domain.entity.MsgMessage;
import com.lingxi.chat.mapper.MsgMessageMapper;
import com.lingxi.chat.service.ConversationService;
import com.lingxi.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 消息处理器
 * <p>
 * 处理所有 WebSocket 消息的收发、已读、撤回、输入中、心跳等事件。
 * 连接时通过URL参数中的Token校验身份，支持离线消息推送。
 * </p>
 * <p>
 * 支持的消息类型（客户端 → 服务端）：
 * - SEND_MESSAGE: 发送消息
 * - MARK_READ: 标记已读
 * - TYPING: 正在输入
 * - RECALL_MESSAGE: 撤回消息（2分钟内）
 * - PING: 心跳
 * </p>
 * <p>
 * 推送的消息类型（服务端 → 客户端）：
 * - NEW_MESSAGE: 新消息
 * - SEND_SUCCESS/SEND_FAIL: 发送结果
 * - MESSAGES_READ: 对方已读
 * - MESSAGE_RECALLED: 消息已撤回
 * - UNREAD_COUNT: 未读数
 * - PONG: 心跳响应
 * - ERROR: 错误
 * </p>
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final ConversationService conversationService;
    private final MsgMessageMapper messageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 连接建立后的处理
     * <p>
     * 1. 从URL参数解析Token（ws://host/ws/message?token=xxx）
     * 2. 校验Token有效性，提取userId
     * 3. 保存Session映射
     * 4. 推送未读数
     * 5. 推送离线消息
     * </p>
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从URL参数获取Token
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        String token = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
        }

        // 校验Token
        if (token == null || token.isEmpty()) {
            sendError(session, 1400, "Token无效，连接被拒绝");
            session.close();
            return;
        }

        Long userId = parseUserId(token);
        if (userId == null) {
            sendError(session, 1400, "Token无效，连接被拒绝");
            session.close();
            return;
        }

        // 保存Session
        session.getAttributes().put("userId", userId);
        sessionManager.addSession(userId, session);

        // 推送未读数
        sendUnreadCount(userId);

        // 推送离线消息
        pushOfflineMessages(userId);

        log.info("WebSocket连接建立: userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 处理客户端发送的文本消息
     * <p>
     * 解析JSON格式消息，根据type字段分发到不同的处理方法。
     * 兼容前端两种格式：data在"data"对象中或在顶层。
     * </p>
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            JsonNode json = objectMapper.readTree(payload);
            String type = json.get("type").asText();

            Long userId = (Long) session.getAttributes().get("userId");
            if (userId == null) {
                sendError(session, 1400, "未认证");
                return;
            }

            // 兼容前端格式：数据可能在 data 对象中，也可能在顶层
            JsonNode dataNode = json.has("data") ? json.get("data") : json;

            switch (type) {
                case ChatConstant.SEND_MESSAGE:
                    handleSendMessage(userId, dataNode);
                    break;
                case ChatConstant.MARK_READ:
                    handleMarkRead(userId, dataNode);
                    break;
                case ChatConstant.TYPING:
                    handleTyping(userId, dataNode);
                    break;
                case ChatConstant.RECALL_MESSAGE:
                    handleRecallMessage(userId, dataNode);
                    break;
                case ChatConstant.PING:
                    handlePing(session);
                    break;
                default:
                    sendError(session, 1403, "消息类型不支持: " + type);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            sendError(session, 1400, "消息格式错误");
        }
    }

    /**
     * 连接关闭后的清理
     * <p>
     * 移除Session映射，更新用户离线状态。
     * </p>
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.removeSession(userId);
            log.info("WebSocket连接关闭: userId={}, status={}", userId, status);
        }
    }

    /**
     * 传输错误处理
     * <p>
     * 网络异常等错误发生时，移除Session映射。
     * </p>
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误: sessionId={}", session.getId(), exception);
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.removeSession(userId);
        }
    }

    /**
     * 处理发送消息
     * <p>
     * 解析消息参数 → 调用ConversationService保存消息 → 返回SEND_SUCCESS给发送者
     * → 异步推送NEW_MESSAGE给对方（如果在线）
     * 失败时返回SEND_FAIL给发送者。
     * </p>
     *
     * @param userId 发送者ID
     * @param json   消息数据（含conversationId, contentType, content等）
     */
    private void handleSendMessage(Long userId, JsonNode json) {
        try {
            // 解析消息参数
            JsonNode conversationIdNode = json.get("conversationId");
            if (conversationIdNode == null || conversationIdNode.isNull()) {
                sendError(userId, 1400, "conversationId不能为空");
                return;
            }
            Long conversationId = conversationIdNode.asLong();

            JsonNode contentTypeNode = json.get("contentType");
            if (contentTypeNode == null || contentTypeNode.isNull()) {
                sendError(userId, 1400, "contentType不能为空");
                return;
            }

            JsonNode contentNode = json.get("content");
            if (contentNode == null || contentNode.isNull()) {
                sendError(userId, 1400, "content不能为空");
                return;
            }

            String msgType = json.has("msgType") ? json.get("msgType").asText() : ChatConstant.MSG_TYPE_TEXT;
            String contentType = contentTypeNode.asText();
            String content = contentNode.asText();
            String mediaUrl = json.has("mediaUrl") && !json.get("mediaUrl").isNull() ? json.get("mediaUrl").asText() : null;
            String fileName = json.has("fileName") && !json.get("fileName").isNull() ? json.get("fileName").asText() : null;
            Long fileSize = json.has("fileSize") && !json.get("fileSize").isNull() ? json.get("fileSize").asLong() : null;

            // 调用服务发送消息
            Long messageId = conversationService.sendMessage(
                    userId, conversationId, msgType, contentType, content, mediaUrl, fileName, fileSize);

            // 发送成功响应给发送者
            Map<String, Object> response = new HashMap<>();
            response.put("type", ChatConstant.SEND_SUCCESS);
            Map<String, Object> data = new HashMap<>();
            data.put("messageId", messageId);
            data.put("conversationId", conversationId);
            data.put("status", ChatConstant.MESSAGE_STATUS_SENT);
            response.put("data", data);
            sessionManager.sendMessage(userId, objectMapper.writeValueAsString(response));

            log.info("消息发送成功: userId={}, messageId={}, conversationId={}", userId, messageId, conversationId);
        } catch (Exception e) {
            log.error("发送消息失败: userId={}", userId, e);
            // 返回失败状态给前端
            Map<String, Object> failResponse = new HashMap<>();
            failResponse.put("type", ChatConstant.SEND_FAIL);
            Map<String, Object> failData = new HashMap<>();
            failData.put("conversationId", json.get("conversationId") != null ? json.get("conversationId").asLong() : null);
            failData.put("status", ChatConstant.MESSAGE_STATUS_FAILED);
            failData.put("errorCode", 1500);
            failData.put("errorMessage", "消息发送失败: " + e.getMessage());
            failResponse.put("data", failData);
            try {
                sessionManager.sendMessage(userId, objectMapper.writeValueAsString(failResponse));
            } catch (Exception ex) {
                log.error("发送失败消息失败", ex);
            }
        }
    }

    /**
     * 处理正在输入
     * <p>
     * 收到 TYPING 事件后，校验用户是会话成员，
     * 然后转发给会话中的其他在线成员（不转发给自己）。
     * </p>
     *
     * @param userId 发送者ID
     * @param json   数据（含conversationId）
     */
    private void handleTyping(Long userId, JsonNode json) {
        try {
            JsonNode conversationIdNode = json.get("conversationId");
            if (conversationIdNode == null || conversationIdNode.isNull()) {
                sendError(userId, 1400, "conversationId不能为空");
                return;
            }
            Long conversationId = conversationIdNode.asLong();

            // 校验用户是否是会话成员
            MsgConversationMember member = conversationService.getConversationMember(conversationId, userId);
            if (member == null || member.getIsDeleted() == 1) {
                sendError(userId, 1403, "无权操作此会话");
                return;
            }

            // 构建转发消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", ChatConstant.TYPING);
            Map<String, Object> data = new HashMap<>();
            data.put("conversationId", conversationId);
            data.put("senderId", userId);
            response.put("data", data);
            String messageJson = objectMapper.writeValueAsString(response);

            // 转发给除发送者外的其他成员
            List<Long> memberIds = conversationService.getConversationMemberIds(conversationId);
            for (Long memberId : memberIds) {
                if (!memberId.equals(userId) && sessionManager.isOnline(memberId)) {
                    sessionManager.sendMessage(memberId, messageJson);
                }
            }

            log.debug("TYPING 事件转发: userId={}, conversationId={}", userId, conversationId);
        } catch (Exception e) {
            log.error("处理 TYPING 事件失败: userId={}", userId, e);
        }
    }

    /**
     * 处理消息撤回
     * <p>
     * 校验规则：
     * 1. 消息存在
     * 2. 消息属于当前会话
     * 3. 发送者是本人
     * 4. 消息发送时间在2分钟内
     * </p>
     * <p>
     * 校验通过后，更新消息内容为"xxx撤回了一条消息"，
     * 广播 MESSAGE_RECALLED 事件给会话所有在线成员。
     * </p>
     *
     * @param userId 撤回者ID
     * @param json   数据（含conversationId, messageId）
     */
    private void handleRecallMessage(Long userId, JsonNode json) {
        try {
            JsonNode conversationIdNode = json.get("conversationId");
            JsonNode messageIdNode = json.get("messageId");

            if (conversationIdNode == null || conversationIdNode.isNull()) {
                sendError(userId, 1400, "conversationId不能为空");
                return;
            }
            if (messageIdNode == null || messageIdNode.isNull()) {
                sendError(userId, 1400, "messageId不能为空");
                return;
            }

            Long conversationId = conversationIdNode.asLong();
            Long messageId = messageIdNode.asLong();

            // 1. 查询消息
            MsgMessage message = messageMapper.selectById(messageId);
            if (message == null) {
                sendError(userId, 1502, "消息不存在");
                return;
            }

            // 2. 验证会话归属
            if (!message.getConversationId().equals(conversationId)) {
                sendError(userId, 1502, "消息不属于此会话");
                return;
            }

            // 3. 验证发送者是否是消息作者
            if (!message.getSenderId().equals(userId)) {
                sendError(userId, 1503, "只能撤回自己发送的消息");
                return;
            }

            // 4. 验证是否在2分钟内
            long messageTime = message.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long now = System.currentTimeMillis();
            if (now - messageTime > ChatConstant.RECALL_TIME_LIMIT_MS) {
                sendError(userId, 1504, "消息发送超过2分钟，无法撤回");
                return;
            }

            // 5. 更新消息内容
            int rows = messageMapper.recallMessage(messageId, userId);
            if (rows == 0) {
                sendError(userId, 1502, "撤回失败，消息不存在或无权限");
                return;
            }

            // 6. 广播 MESSAGE_RECALLED 给会话所有成员
            Map<String, Object> response = new HashMap<>();
            response.put("type", ChatConstant.MESSAGE_RECALLED);
            Map<String, Object> data = new HashMap<>();
            data.put("conversationId", conversationId);
            data.put("messageId", messageId);
            response.put("data", data);
            String messageJson = objectMapper.writeValueAsString(response);

            List<Long> memberIds = conversationService.getConversationMemberIds(conversationId);
            for (Long memberId : memberIds) {
                if (sessionManager.isOnline(memberId)) {
                    sessionManager.sendMessage(memberId, messageJson);
                }
            }

            log.info("消息撤回成功: userId={}, messageId={}, conversationId={}", userId, messageId, conversationId);
        } catch (Exception e) {
            log.error("处理消息撤回失败: userId={}", userId, e);
            sendError(userId, 1500, "消息撤回失败: " + e.getMessage());
        }
    }

    /**
     * 处理标记已读
     * <p>
     * 调用ConversationService标记会话已读，
     * 然后推送 MESSAGES_READ 事件给对方（不是自己）。
     * </p>
     *
     * @param userId 当前用户ID
     * @param json   数据（含conversationId）
     */
    private void handleMarkRead(Long userId, JsonNode json) {
        try {
            JsonNode conversationIdNode = json.get("conversationId");
            if (conversationIdNode == null || conversationIdNode.isNull()) {
                sendError(userId, 1400, "conversationId不能为空");
                return;
            }
            Long conversationId = conversationIdNode.asLong();

            // 调用服务标记已读
            conversationService.markAsRead(userId, conversationId);

            // 构建已读消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", ChatConstant.MESSAGES_READ);
            Map<String, Object> data = new HashMap<>();
            data.put("conversationId", conversationId);
            data.put("readByUserId", userId);
            data.put("readAt", System.currentTimeMillis());
            response.put("data", data);

            // 推送给对方（不是自己）
            List<Long> memberIds = conversationService.getConversationMemberIds(conversationId);
            for (Long memberId : memberIds) {
                if (!memberId.equals(userId) && sessionManager.isOnline(memberId)) {
                    sessionManager.sendMessage(memberId, objectMapper.writeValueAsString(response));
                }
            }

            log.info("标记已读成功: userId={}, conversationId={}", userId, conversationId);
        } catch (Exception e) {
            log.error("标记已读失败: userId={}", userId, e);
            sendError(userId, 1501, "标记已读失败: " + e.getMessage());
        }
    }

    /**
     * 处理心跳
     * <p>
     * 续期用户的Redis在线状态，返回PONG响应。
     * 前端每30秒发送一次PING，超时5分钟未收到则判定离线。
     * </p>
     *
     * @param session WebSocket会话
     */
    private void handlePing(WebSocketSession session) {
        try {
            // 续期在线状态
            Long userId = (Long) session.getAttributes().get("userId");
            if (userId != null) {
                sessionManager.refreshOnlineStatus(userId);
            }

            Map<String, Object> pong = new HashMap<>();
            pong.put("type", ChatConstant.PONG);
            pong.put("timestamp", System.currentTimeMillis());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
        } catch (Exception e) {
            log.error("发送PONG失败", e);
        }
    }

    /**
     * 推送未读数给用户
     * <p>
     * 连接建立时调用，推送消息未读数和通知未读数。
     * </p>
     *
     * @param userId 用户ID
     */
    private void sendUnreadCount(Long userId) {
        try {
            // 查询消息未读数
            int messageCount = conversationService.getUnreadCount(userId);

            // 查询通知未读数（暂时返回0，后续实现）
            int notificationCount = 0;

            Map<String, Object> msg = new HashMap<>();
            msg.put("type", ChatConstant.UNREAD_COUNT);
            Map<String, Object> data = new HashMap<>();
            data.put("messageCount", messageCount);
            data.put("notificationCount", notificationCount);
            msg.put("data", data);
            sessionManager.sendMessage(userId, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("推送未读数失败: userId={}", userId, e);
        }
    }

    /**
     * 推送离线消息
     * <p>
     * 连接建立时调用，查询用户的所有未读消息并逐条推送。
     * 保证用户不丢失任何消息。
     * </p>
     *
     * @param userId 用户ID
     */
    private void pushOfflineMessages(Long userId) {
        try {
            // 查询用户的未读消息
            List<MsgMessage> offlineMessages = messageMapper.selectUnreadByUserId(userId);
            if (offlineMessages.isEmpty()) {
                return;
            }

            log.info("推送离线消息: userId={}, count={}", userId, offlineMessages.size());

            // 逐条推送离线消息
            for (MsgMessage message : offlineMessages) {
                Map<String, Object> pushMsg = new HashMap<>();
                pushMsg.put("type", ChatConstant.NEW_MESSAGE);
                Map<String, Object> data = new HashMap<>();
                data.put("messageId", message.getId());
                data.put("conversationId", message.getConversationId());
                data.put("senderId", message.getSenderId());
                data.put("msgType", message.getMsgType());
                data.put("contentType", message.getContentType());
                data.put("content", message.getContent());
                data.put("mediaUrl", message.getMediaUrl());
                data.put("fileName", message.getFileName());
                data.put("fileSize", message.getFileSize());
                data.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().format(DATE_FORMATTER) : null);
                pushMsg.put("data", data);
                sessionManager.sendMessage(userId, objectMapper.writeValueAsString(pushMsg));
            }
        } catch (Exception e) {
            log.error("推送离线消息失败: userId={}", userId, e);
        }
    }

    /**
     * 解析JWT Token获取用户ID
     *
     * @param token JWT Token字符串
     * @return 用户ID，解析失败返回null
     */
    private Long parseUserId(String token) {
        try {
            Claims claims = JwtUtil.parseToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.warn("Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 发送错误消息给指定Session
     *
     * @param session WebSocket会话
     * @param code    错误码
     * @param message 错误消息
     */
    private void sendError(WebSocketSession session, int code, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", ChatConstant.ERROR);
            Map<String, Object> data = new HashMap<>();
            data.put("errorCode", code);
            data.put("errorMessage", message);
            error.put("data", data);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 发送错误消息给指定用户
     *
     * @param userId  目标用户ID
     * @param code    错误码
     * @param message 错误消息
     */
    private void sendError(Long userId, int code, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", ChatConstant.ERROR);
            Map<String, Object> data = new HashMap<>();
            data.put("errorCode", code);
            data.put("errorMessage", message);
            error.put("data", data);
            sessionManager.sendMessage(userId, objectMapper.writeValueAsString(error));
        } catch (Exception e) {
            log.error("发送错误消息失败: userId={}", userId, e);
        }
    }
}

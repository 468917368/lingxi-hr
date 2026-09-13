package com.lingxi.chat.controller;

import com.lingxi.chat.domain.dto.CreateConversationRequest;
import com.lingxi.chat.domain.dto.SendMessageRequest;
import com.lingxi.chat.domain.vo.ConversationVO;
import com.lingxi.chat.domain.vo.MessageVO;
import com.lingxi.chat.service.ConversationService;
import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 会话控制器
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@RequireLogin
@RequireRole({"CANDIDATE", "HR", "INTERVIEWER"})
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 获取会话列表
     * GET /api/v1/conversations
     *
     * @return 会话列表
     */
    @RequireLogin
    @GetMapping
    public Result<List<ConversationVO>> getConversations() {
        Long userId = UserContext.getUserId();
        return Result.success(conversationService.getConversations(userId));
    }

    /**
     * 创建会话
     * POST /api/v1/conversations
     *
     * @param request 创建请求
     * @return 会话ID
     */
    @RequireLogin
    @PostMapping
    public Result<Long> createConversation(@RequestBody @Valid CreateConversationRequest request) {
        Long userId = UserContext.getUserId();
        Long conversationId = conversationService.createConversation(userId, request);
        return Result.success(conversationId);
    }

    /**
     * 获取会话详情
     * GET /api/v1/conversations/{id}
     *
     * @param id 会话ID
     * @return 会话详情
     */
    @RequireLogin
    @GetMapping("/{id}")
    public Result<ConversationVO> getConversationDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(conversationService.getConversationDetail(userId, id));
    }

    /**
     * 获取聊天记录
     * GET /api/v1/conversations/{id}/messages?page=1&size=20&sinceId=xxx
     *
     * @param id      会话ID
     * @param page    页码
     * @param size    每页条数
     * @param sinceId 增量加载：只返回此ID之后的消息（可选）
     * @return 消息列表
     */
    @RequireLogin
    @GetMapping("/{id}/messages")
    public Result<?> getMessages(
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "sinceId", required = false) Long sinceId) {
        Long userId = UserContext.getUserId();
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        // 增量加载：只返回新消息
        if (sinceId != null && sinceId > 0) {
            List<MessageVO> newMessages = conversationService.getMessagesAfterId(userId, id, sinceId);
            return Result.success(newMessages);
        }

        // 首次加载：分页
        return Result.success(conversationService.getMessages(userId, id, page, size));
    }

    /**
     * 发送消息
     * POST /api/v1/conversations/{id}/messages
     *
     * @param id      会话ID
     * @param request 发送请求
     * @return 消息ID
     */
    @RequireLogin
    @PostMapping("/{id}/messages")
    public Result<Long> sendMessage(@PathVariable Long id,
                                    @RequestBody @Valid SendMessageRequest request) {
        Long userId = UserContext.getUserId();
        Long messageId = conversationService.sendMessage(
                userId, id, request.getMsgType(), request.getContentType(), request.getContent(),
                request.getMediaUrl(), request.getFileName(), request.getFileSize());
        return Result.success(messageId);
    }

    /**
     * 删除会话（仅对自己隐藏）
     * DELETE /api/v1/conversations/{id}
     *
     * @param id 会话ID
     * @return 操作结果
     */
    @RequireLogin
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        conversationService.deleteConversation(userId, id);
        return Result.success();
    }

    /**
     * 设置会话置顶
     * PUT /api/v1/conversations/{id}/top?isTop=true
     *
     * @param id    会话ID
     * @param isTop 是否置顶
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/{id}/top")
    public Result<Void> setTop(@PathVariable Long id,
                               @RequestParam("isTop") boolean isTop) {
        Long userId = UserContext.getUserId();
        conversationService.setTop(userId, id, isTop);
        return Result.success();
    }

    /**
     * 设置会话免打扰
     * PUT /api/v1/conversations/{id}/muted?isMuted=true
     *
     * @param id      会话ID
     * @param isMuted 是否免打扰
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/{id}/muted")
    public Result<Void> setMuted(@PathVariable Long id,
                                 @RequestParam("isMuted") boolean isMuted) {
        Long userId = UserContext.getUserId();
        conversationService.setMuted(userId, id, isMuted);
        return Result.success();
    }
}

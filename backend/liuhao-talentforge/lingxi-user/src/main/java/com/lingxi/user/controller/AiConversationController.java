package com.lingxi.user.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.user.domain.vo.AiSessionVO;
import com.lingxi.user.service.AiConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI会话控制器
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationService aiConversationService;

    /**
     * 获取AI会话列表
     * GET /api/v1/ai/conversations?page=1&size=20
     *
     * @param page 页码
     * @param size 每页条数
     * @return 会话列表
     */
    @RequireLogin
    @GetMapping
    public Result<PageResult<AiSessionVO>> getSessions(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        return Result.success(aiConversationService.getSessions(userId, page, size));
    }

    /**
     * 创建新会话
     * POST /api/v1/ai/conversations
     *
     * @return 会话ID
     */
    @RequireLogin
    @PostMapping
    public Result<String> createSession() {
        Long userId = UserContext.getUserId();
        String sessionId = aiConversationService.createSession(userId);
        return Result.success(sessionId);
    }

    /**
     * 获取会话详情
     * GET /api/v1/ai/conversations/{sessionId}
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @RequireLogin
    @GetMapping("/{sessionId}")
    public Result<AiSessionVO> getSessionDetail(@PathVariable String sessionId) {
        Long userId = UserContext.getUserId();
        return Result.success(aiConversationService.getSessionDetail(userId, sessionId));
    }

    /**
     * 删除会话
     * DELETE /api/v1/ai/conversations/{sessionId}
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @RequireLogin
    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = UserContext.getUserId();
        aiConversationService.deleteSession(userId, sessionId);
        return Result.success();
    }
}

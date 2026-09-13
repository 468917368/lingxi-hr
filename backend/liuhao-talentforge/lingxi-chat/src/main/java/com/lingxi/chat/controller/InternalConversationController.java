package com.lingxi.chat.controller;

import com.lingxi.chat.domain.dto.CreateConversationRequest;
import com.lingxi.chat.service.ConversationService;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 内部会话接口（供其他微服务调用）
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/internal/conversations")
@RequiredArgsConstructor
public class InternalConversationController {

    private final ConversationService conversationService;

    /**
     * 创建会话（供HR/简历服务调用）
     * POST /internal/conversations
     *
     * @param request 创建请求
     * @return 会话ID
     */
    @PostMapping
    public Result<Map<String, Long>> createConversation(@RequestBody @Valid CreateConversationRequest request) {
        // 使用hrId作为当前用户
        Long conversationId = conversationService.createConversation(request.getHrId(), request);

        Map<String, Long> result = new HashMap<>();
        result.put("conversationId", conversationId);
        log.info("内部创建会话成功: conversationId={}, companyId={}", conversationId, request.getCompanyId());
        return Result.success(result);
    }
}

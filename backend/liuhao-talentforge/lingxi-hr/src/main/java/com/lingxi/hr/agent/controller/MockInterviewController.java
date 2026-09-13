package com.lingxi.hr.agent.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.hr.agent.MockInterviewAgent;
import com.lingxi.hr.agent.dto.MockAnswerRequest;
import com.lingxi.hr.agent.dto.MockGenerateRequest;
import com.lingxi.hr.agent.dto.MockQuestionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mock Interview 接口（5 个，3 个 SSE）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mock-interview")
@RequiredArgsConstructor
@RequireLogin
public class MockInterviewController {

    private final MockInterviewAgent mockInterviewAgent;

    /** 生成题目（SSE） */
    @PostMapping("/generate")
    public SseEmitter generate(@RequestBody MockGenerateRequest request) {
        Long candidateId = requireUserId();
        SseEmitter emitter = createEmitter();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture.runAsync(() -> runWithContext(requestAttributes, emitter,
                () -> mockInterviewAgent.generate(request, candidateId, listener(emitter))));
        return emitter;
    }

    /** 获取题目（继续面试） */
    @GetMapping("/{sessionId}/questions")
    public Result<List<MockQuestionVO>> questions(@PathVariable String sessionId) {
        Long candidateId = requireUserId();
        return Result.success(mockInterviewAgent.getQuestions(sessionId, candidateId));
    }

    /** 提交答案（SSE，返回评分） */
    @PostMapping("/{sessionId}/answer")
    public SseEmitter answer(@PathVariable String sessionId, @RequestBody MockAnswerRequest request) {
        Long candidateId = requireUserId();
        SseEmitter emitter = createEmitter();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture.runAsync(() -> runWithContext(requestAttributes, emitter,
                () -> mockInterviewAgent.answer(sessionId, request, candidateId, listener(emitter))));
        return emitter;
    }

    /** 跳过题目 */
    @PostMapping("/{sessionId}/skip")
    public Result<Void> skip(@PathVariable String sessionId, @RequestParam("questionNumber") Integer questionNumber) {
        Long candidateId = requireUserId();
        mockInterviewAgent.skip(sessionId, questionNumber, candidateId);
        return Result.success();
    }

    /** 生成报告（SSE） */
    @GetMapping("/{sessionId}/report")
    public SseEmitter report(@PathVariable String sessionId) {
        Long candidateId = requireUserId();
        SseEmitter emitter = createEmitter();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture.runAsync(() -> runWithContext(requestAttributes, emitter,
                () -> mockInterviewAgent.report(sessionId, candidateId, listener(emitter))));
        return emitter;
    }

    // ==================== 私有方法 ====================

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private SseEmitter createEmitter() {
        return new SseEmitter(0L);
    }

    private MockInterviewAgent.AgentEventListener listener(SseEmitter emitter) {
        return (event, data) -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private void runSse(SseEmitter emitter, Runnable task) {
        try {
            task.run();
            emitter.complete();
        } catch (Exception e) {
            log.error("Mock Interview SSE 执行失败", e);
            sendError(emitter, e);
            emitter.complete();
        }
    }

    /**
     * 在异步线程执行任务，并临时绑定请求上下文，
     * 使 Feign token 透传拦截器能拿到当前请求的 Authorization。
     */
    private void runWithContext(RequestAttributes attrs, SseEmitter emitter, Runnable task) {
        if (attrs != null) {
            RequestContextHolder.setRequestAttributes(attrs);
        }
        try {
            runSse(emitter, task);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private void sendError(SseEmitter emitter, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        if (e instanceof BusinessException) {
            error.put("code", ((BusinessException) e).getCode());
            error.put("message", ((BusinessException) e).getMessage());
        } else {
            error.put("code", 500);
            error.put("message", "系统繁忙，请稍后重试");
        }
        try {
            emitter.send(SseEmitter.event().name("error").data(error));
        } catch (IOException ex) {
            log.debug("SSE error 事件推送失败", ex);
        }
    }
}

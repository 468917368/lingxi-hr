package com.lingxi.user.agent.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.user.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lingxi.common.domain.PageResult;
import com.lingxi.user.domain.entity.SysAgentConversation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Job Agent 接口（SSE 流式对话 + 会话管理）
 * <p>
 * 提供AI岗位智能管家的对话接口，支持：
 * - SSE流式对话：边生成边推送，用户感知快
 * - 会话管理：创建/列表/历史/删除
 * - 安全控制：频率限制、并发控制、输入过滤
 * - 任务取消：用户可随时停止生成
 * </p>
 * <p>
 * SSE事件类型（通过 @GetMapping(value="/chat", produces="text/event-stream") 推送）：
 * - progress: 进度提示（如"正在搜索岗位..."），前端显示 loading
 * - chunk: 流式文本片段，前端逐字显示
 * - result: 完整回答，前端渲染最终内容
 * - done: 对话结束，前端关闭连接
 * - error: 错误，前端显示错误提示
 * - stopped: 用户主动停止，前端显示"已停止生成"
 * </p>
 * <p>
 * 核心流程：
 * 前端 GET /chat?message=xxx → 建立 SSE 连接 → 异步调 JobAgentService.chat()
 * → Agent 每产生一点数据就通过 listener 推送给前端 → 完成后关闭连接
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@RequireLogin
public class AgentController {

    private final JobAgentService jobAgentService;
    private final AgentConversationManager conversationManager;
    private final AgentInputSanitizer inputSanitizer;
    private final AgentRateLimiter rateLimiter;
    private final AgentConcurrencyLimiter concurrencyLimiter;

    /** 正在运行的任务 {sessionId: CompletableFuture}，用于支持任务取消 */
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> RUNNING_TASKS = new ConcurrentHashMap<>();

    /**
     * SSE 流式对话
     * <p>
     * 前端通过SSE连接此接口，边生成边推送回答。
     * 处理流程：频率限制 → 并发控制 → 输入过滤 → 异步执行Agent → 流式返回
     * </p>
     *
     * @param sessionId 会话ID（UUID格式，由前端生成或调用 /sessions 创建）
     * @param message   用户消息内容
     * @param response  HTTP响应（设置SSE头）
     * @return SseEmitter 流式响应
     */
    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam String sessionId,
                           @RequestParam String message,
                           javax.servlet.http.HttpServletResponse response) {
        Long userId = requireUserId();
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");

        // ① 频率限制
        if (!rateLimiter.tryAcquire(userId)) {
            return errorEmitter("请求过于频繁，请稍后再试");
        }

        // ② 并发控制
        if (!concurrencyLimiter.tryAcquire(userId)) {
            return errorEmitter("您有正在进行的对话，请等待完成");
        }

        // ③ 输入安全处理
        String sanitized = inputSanitizer.sanitize(message);
        if (sanitized == null) {
            concurrencyLimiter.release(userId);
            return errorEmitter("消息内容不安全，请重新输入");
        }

        // 创建 SSE 发射器（0L = 不超时，Agent 完成后手动 complete）
        SseEmitter emitter = new SseEmitter(0L);
        // 保存当前请求上下文（异步线程无法自动继承，需手动传递）
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        String finalMessage = sanitized;

        // 异步执行 Agent 对话（不阻塞 HTTP 请求线程）
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            // 恢复请求上下文（Feign 调用需要 UserContext）
            RequestContextHolder.setRequestAttributes(attrs);
            try {
                // 调用 Agent 核心服务（流式生成，通过 listener 实时推送 SSE 事件）
                jobAgentService.chat(userId, sessionId, finalMessage, listener(emitter));
                emitter.complete();
            } catch (CancellationException e) {
                log.info("Agent 任务被取消: sessionId={}", sessionId);
                sendCancelled(emitter);
                emitter.complete();
            } catch (Exception e) {
                log.error("Agent 异常", e);
                sendError(emitter, e);
                emitter.complete();
            } finally {
                // 清理资源：移除任务记录、释放并发令牌、重置请求上下文
                RUNNING_TASKS.remove(sessionId);
                concurrencyLimiter.release(userId);
                RequestContextHolder.resetRequestAttributes();
            }
        });

        // 注册任务（支持用户通过 /chat/stop 取消）
        RUNNING_TASKS.put(sessionId, task);
        return emitter;
    }

    /**
     * 停止当前对话
     * <p>
     * 取消正在运行的Agent任务，前端收到stopped事件后关闭SSE连接。
     * 校验会话归属，防止用户停止他人的对话。
     * </p>
     *
     * @param sessionId 要停止的会话ID
     * @return 操作结果
     */
    @PostMapping("/chat/stop")
    public Result<Void> stopChat(@RequestParam String sessionId) {
        Long userId = requireUserId();

        // 验证会话归属
        Long ownerId = conversationManager.getSessionOwnerId(sessionId);
        if (ownerId != null && !ownerId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        CompletableFuture<Void> task = RUNNING_TASKS.remove(sessionId);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            log.info("Agent 任务已停止: userId={}, sessionId={}", userId, sessionId);
        }

        return Result.success();
    }

    /**
     * 创建新会话
     * <p>
     * 生成一个UUID格式的sessionId，用于后续对话。
     * 前端也可以自己生成UUID，不一定要调这个接口。
     * </p>
     *
     * @return 新会话的sessionId
     */
    @PostMapping("/sessions")
    public Result<String> createSession() {
        Long userId = requireUserId();
        return Result.success(conversationManager.createSession(userId));
    }

    /**
     * 获取用户的会话列表（分页）
     * <p>
     * 返回每个会话的sessionId、最后一条消息、最后角色、更新时间。
     * </p>
     *
     * @param page 页码（默认1）
     * @param size 每页条数（默认20）
     * @return 分页会话列表
     */
    @GetMapping("/sessions")
    public Result<PageResult<Map<String, Object>>> listSessions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = requireUserId();
        List<SysAgentConversation> sessions = conversationManager.getUserSessions(userId, page, size);
        long total = conversationManager.countUserSessions(userId);

        // 转换为前端需要的格式
        List<Map<String, Object>> list = sessions.stream().map(msg -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", msg.getSessionId());
            item.put("lastMessage", msg.getContent());
            item.put("lastRole", msg.getRole());
            item.put("updatedAt", msg.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        return Result.success(PageResult.of(list, total, page, size));
    }

    /**
     * 获取会话历史消息
     * <p>
     * 返回指定会话的完整对话历史（最多50条），过滤掉system消息。
     * 校验会话归属，只能查看自己的会话。
     * </p>
     *
     * @param sessionId 会话ID
     * @return 消息列表（role + content + createdAt）
     * @throws BusinessException 404 会话不存在 / 403 无权访问
     */
    @GetMapping("/sessions/{sessionId}/history")
    public Result<List<Map<String, Object>>> getSessionHistory(@PathVariable String sessionId) {
        Long userId = requireUserId();
        Long ownerId = conversationManager.getSessionOwnerId(sessionId);
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!ownerId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<SysAgentConversation> history = conversationManager.getHistory(userId, sessionId, 50);
        List<Map<String, Object>> list = history.stream()
                .filter(msg -> !"system".equals(msg.getRole()))
                .map(msg -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", msg.getRole());
                    item.put("content", msg.getContent());
                    item.put("createdAt", msg.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 删除会话
     * <p>
     * 校验会话归属后，删除指定会话及其所有历史消息。
     * </p>
     *
     * @param sessionId 会话ID
     * @return 操作结果
     * @throws BusinessException 404 会话不存在 / 403 无权访问
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = requireUserId();
        Long ownerId = conversationManager.getSessionOwnerId(sessionId);
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!ownerId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        conversationManager.deleteSession(userId, sessionId);
        return Result.success();
    }

    // ==================== 私有方法 ====================

    /**
     * 获取当前登录用户ID（未登录则抛异常）
     */
    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 创建SSE事件监听器
     * <p>
     * 将AgentService的事件回调转发为SSE事件推送给前端。
     * </p>
     */
    private JobAgentService.AgentEventListener listener(SseEmitter emitter) {
        return (event, data) -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * 创建错误SSE响应（前置校验失败时使用）
     */
    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", 400);
            error.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(error));
        } catch (IOException ignored) {
        }
        emitter.complete();
        return emitter;
    }

    /**
     * 发送错误事件（Agent执行异常时使用）
     * <p>
     * 区分BusinessException（业务错误，返回具体code）和其他异常（系统错误，返回500）。
     * </p>
     */
    private void sendError(SseEmitter emitter, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        if (e instanceof BusinessException) {
            error.put("code", ((BusinessException) e).getCode());
            error.put("message", e.getMessage());
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

    /**
     * 发送任务取消事件（用户主动停止时使用）
     */
    private void sendCancelled(SseEmitter emitter) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "已停止生成");
        try {
            emitter.send(SseEmitter.event().name("stopped").data(data));
        } catch (IOException ex) {
            log.debug("SSE stopped 事件推送失败", ex);
        }
    }
}

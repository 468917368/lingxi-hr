package com.lingxi.resume.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.resume.agent.ResumeParseService;
import com.lingxi.resume.domain.vo.AbilityModelVO;
import com.lingxi.resume.domain.vo.ParseTaskStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 简历解析控制器
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-2 解析进度 SSE + 能力模型查询。
 *
 * <p>SSE 事件类型：{@code thinking / tool_call / tool_result / final / error / heartbeat}。
 * Servlet SseEmitter + 解析线程池（不引 WebFlux），心跳 15s、连接超时 60s。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeParseController {

    /** 连接超时（毫秒） */
    private static final long SSE_TIMEOUT_MS = 60_000L;

    /**
     * 心跳间隔（秒）
     * <p>需小于代理层空闲超时（Node dev server / Nginx keepalive 默认 5s）：
     * 若心跳间隔大于代理空闲超时，长解析（LLM 生成几十秒）期间连接会被代理判定空闲断开，
     * 前端 fetch 流中断收不到 final。3s 心跳可保证连接持续活跃。
     */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 3L;

    private final ResumeParseService resumeParseService;

    /** 心跳调度器（daemon） */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "resume-parse-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /** 活跃心跳任务：emitter → future */
    private final Map<SseEmitter, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    /**
     * 解析进度 SSE 流
     *
     * <p>事件序列（按状态）：
     * <ul>
     *   <li>解析中：thinking → tool_call → tool_result → final</li>
     *   <li>已完成：直接 final（历史结果）</li>
     *   <li>失败：error</li>
     *   <li>不存在：error(3101)</li>
     * </ul>
     */
    @GetMapping(value = "/{resumeId}/parse-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter parseStream(@PathVariable("resumeId") Long resumeId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 心跳：客户端断开/超时后自动取消
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(""));
            } catch (Exception e) {
                // 客户端已断开，心跳自动终止（onCompletion 里清理）
                log.debug("SSE 心跳发送失败: resumeId={}", resumeId);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        heartbeatTasks.put(emitter, heartbeat);

        emitter.onCompletion(() -> cancelHeartbeat(emitter));
        emitter.onTimeout(() -> cancelHeartbeat(emitter));
        emitter.onError(t -> cancelHeartbeat(emitter));

        log.info("SSE 订阅解析进度: resumeId={}", resumeId);
        resumeParseService.subscribeAndWait(resumeId, emitter);
        return emitter;
    }

    /**
     * 解析任务状态查询（异步化轮询用：跳页/断连后回页轮询的完成信号）
     *
     * <p>返回 RUNNING/COMPLETED/FAILED/NONE；前端据此决定继续轮询或刷新列表。
     */
    @GetMapping("/{resumeId}/parse-status")
    public Result<ParseTaskStatusVO> parseStatus(@PathVariable("resumeId") Long resumeId) {
        return Result.success(resumeParseService.getTaskStatus(resumeId));
    }

    /**
     * 能力模型查询（雷达图数据）
     */
    @GetMapping("/{id}/ability-model")
    public Result<AbilityModelVO> abilityModel(@PathVariable("id") Long id) {
        return Result.success(resumeParseService.getAbilityModel(id));
    }

    @PreDestroy
    public void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdownNow();
    }

    private void cancelHeartbeat(SseEmitter emitter) {
        ScheduledFuture<?> future = heartbeatTasks.remove(emitter);
        if (future != null) {
            future.cancel(true);
        }
    }
}

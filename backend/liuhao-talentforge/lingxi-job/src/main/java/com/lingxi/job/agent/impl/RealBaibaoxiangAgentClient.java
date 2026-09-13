package com.lingxi.job.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Real 百宝箱智能体客户端（{@code ai.agent.mock=false} 生效，联调用）
 * <p>
 * 企业版开放平台 {@code POST {baseUrl}/api/chat} 协议（A2A/JSON-RPC 已废弃）：
 * <ul>
 *   <li>请求头仅 {@code Authorization} 原值 + {@code Content-Type: application/json}（流式额外 {@code text/event-stream}）</li>
 *   <li>请求体 {@code {appId, query, userId, stream}}；JD 解析 {@code stream=false}，面试出题 {@code stream=true}</li>
 *   <li>{@code userId} 为 {@code BaibaoxiangUserIdProvider} 生成的伪标识，不透传内部用户 ID</li>
 * </ul>
 * </p>
 * <p>
 * 响应解析（按 Task 0 已核实样本）：
 * <ul>
 *   <li><b>非流式</b>：根级 {@code errorCode=="0" && success==true}，模型文本 {@code data.result[0].chunk}（{@code mediaType=text}）</li>
 *   <li><b>流式</b>：外层 {@code event}/{@code data}；{@code data} 为 JSON {@code {type, payload(JSON字符串)}} 需二次解析：
 *   {@code header}→HEADER、{@code chunk.payload.text}→CONTENT、{@code event:end 且 data.type=end}→COMPLETE；
 *   {@code meta}/{@code thinking} 直接丢弃，不记录不输出</li>
 * </ul>
 * </p>
 * <p>
 * 安全：Token 仅来自 {@code BAIBAOXIANG_AUTHORIZATION} 环境变量；日志只记录 baseUrl/脱敏 appId/userId 长度；
 * 不透传 thinking、不记录 Token、原始请求体与敏感报文。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.agent.mock", havingValue = "false")
public class RealBaibaoxiangAgentClient implements BaibaoxiangAgentClient, DisposableBean {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String authorization;
    private final String jdParseAppId;
    private final String interviewAppId;

    /** 流式（面试出题）总超时（毫秒）：上游未正常结束/连接保持时强制结束，避免前端无限 loading */
    private final long streamTimeoutMs;

    /** 流式读取执行线程（daemon，随 JVM 退出；总超时依赖其可中断） */
    private final ExecutorService streamExecutor;

    /** 上游 SSE 结束语义事件名集合（end/done/completed/...，兼容 conversation.* 后缀变体） */
    private static final Set<String> TERMINAL_EVENTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("end", "done", "completed", "complete", "finish", "stop")));

    public RealBaibaoxiangAgentClient(ObjectMapper objectMapper,
                                      RestTemplateBuilder builder,
                                      @Value("${baibaoxiang.base-url:https://api.tbox.cn}") String baseUrl,
                                      @Value("${baibaoxiang.authorization:}") String authorization,
                                      @Value("${baibaoxiang.user-id-hmac-key:}") String userHmacKey,
                                      @Value("${baibaoxiang.jd-parse-app-id:}") String jdParseAppId,
                                      @Value("${baibaoxiang.interview-app-id:}") String interviewAppId,
                                      @Value("${baibaoxiang.connect-timeout-ms:3000}") long connectTimeoutMs,
                                      @Value("${baibaoxiang.read-timeout-ms:30000}") long readTimeoutMs,
                                      @Value("${baibaoxiang.stream-timeout-ms:110000}") long streamTimeoutMs,
                                      @Value("${sse.concurrency:10}") int streamConcurrency) {
        // Real 模式启动校验：Token/两个 AppID/HMAC 密钥非空（错误信息不含密钥内容）
        // 注：baseUrl 有默认值（https://api.tbox.cn），恒非空，无需校验
        if (!StringUtils.hasText(authorization)
                || !StringUtils.hasText(userHmacKey)
                || !StringUtils.hasText(jdParseAppId)
                || !StringUtils.hasText(interviewAppId)) {
            throw new IllegalStateException(
                    "百宝箱 Real 模式缺少必要配置（base-url/authorization/user-id-hmac-key/"
                            + "jd-parse-app-id/interview-app-id）。请通过环境变量注入 BAIBAOXIANG_*，"
                            + "严禁将密钥硬编码进配置文件。");
        }
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.baseUrl = baseUrl;
        this.authorization = authorization;
        this.jdParseAppId = jdParseAppId;
        this.interviewAppId = interviewAppId;
        this.streamTimeoutMs = streamTimeoutMs;
        // 受限线程池：最大并发 ≤ SSE 全局并发上限（sse.concurrency），SynchronousQueue 不排队堆积；
        // 超时/异常时主动关闭流使线程必然释放，避免 cached 池无限创建阻塞线程
        this.streamExecutor = new ThreadPoolExecutor(0, streamConcurrency, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, "baibaoxiang-sse-runner");
                    t.setDaemon(true);
                    return t;
                });
    }

    @Override
    public void destroy() {
        streamExecutor.shutdownNow();
    }

    @Override
    public String parseJd(String prompt, String userId) {
        String url = baseUrl + "/api/chat";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildPayload(jdParseAppId, prompt, userId, false), buildHeaders(false));
        log.info("Real 百宝箱 JD 解析请求: baseUrl={}, appId=****, userIdLen={}", baseUrl, userId.length());
        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        return extractNonStreamResult(resp.getBody());
    }

    @Override
    public void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                                Consumer<AgentStreamEvent> consumer) {
        String url = baseUrl + "/api/chat";
        HttpHeaders headers = buildHeaders(true);
        Map<String, Object> payload = buildPayload(interviewAppId, prompt, userId, true);
        RequestCallback requestCallback = request -> {
            request.getHeaders().putAll(headers);
            request.getBody().write(objectMapper.writeValueAsBytes(payload));
        };
        log.info("Real 百宝箱 Interview SSE 请求: baseUrl={}, appId=****, userIdLen={}", baseUrl, userId.length());

        // 终态守卫：COMPLETE/FAILURE 只回调一次（超时竞态下防重复），HEADER/CONTENT 直通
        AtomicBoolean finished = new AtomicBoolean(false);
        Consumer<AgentStreamEvent> guarded = ev -> {
            if (ev.getType() == AgentStreamEvent.Type.COMPLETE || ev.getType() == AgentStreamEvent.Type.FAILURE) {
                if (finished.compareAndSet(false, true)) {
                    consumer.accept(ev);
                    log.info("Real 百宝箱 SSE 终态事件: {}, 流结束", ev.getType());
                } else {
                    log.warn("Real 百宝箱 SSE 流已结束，忽略重复终态事件: {}", ev.getType());
                }
            } else {
                consumer.accept(ev);
            }
        };

        // 独立线程执行阻塞式流读取 + 总超时兜底：保证 streamInterview 必有界返回（前端不无限 loading）
        AtomicReference<ClientHttpResponse> currentResponse = new AtomicReference<>();
        Future<?> future = streamExecutor.submit(() -> {
            try {
                restTemplate.execute(url, HttpMethod.POST, requestCallback,
                        (ClientHttpResponse response) -> {
                            currentResponse.set(response);
                            return consumeStream(response, cancelled, guarded);
                        });
            } catch (Exception e) {
                // 非 2xx / 网络中断 / 连接异常（consumeStream 已处理读取/解析失败）
                guarded.accept(AgentStreamEvent.failure("百宝箱请求失败"));
            }
            return null;
        });
        try {
            future.get(streamTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("Real 百宝箱 SSE 流正常返回");
        } catch (TimeoutException e) {
            log.error("Real 百宝箱 SSE 流超时（{}ms），强制结束，避免前端无限等待", streamTimeoutMs);
            future.cancel(true);
            // 主动关闭底层响应流：让阻塞的 readLine 立即抛 IOException 释放线程（仅 cancel(true) 中断阻塞 IO 不可靠）
            closeResponse(currentResponse.get());
            guarded.accept(AgentStreamEvent.failure("SSE 流超时"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            guarded.accept(AgentStreamEvent.failure("SSE 流读取被中断"));
        } catch (ExecutionException e) {
            log.error("Real 百宝箱 SSE 流执行异常", e);
            future.cancel(true);
            guarded.accept(AgentStreamEvent.failure("百宝箱请求失败"));
        }
    }

    // ==================== 流式帧消费（面试，stream=true） ====================

    /**
     * 逐帧读取上游 SSE 并回调内部事件（HEADER/CONTENT/COMPLETE/FAILURE）
     * <p>结束帧识别（鲁棒，兼容真实百宝箱多种变体）：{@code event}/{@code data.type} 命中
     * {@code end/done/completed/complete/finish/stop}（含 {@code *.completed/*.done/*.end} 后缀，
     * 覆盖 {@code conversation.chat.completed}）、{@code data.status=completed/done}、data 为
     * {@code [DONE]}。结束帧缺失 → 流尾 EOF 或总超时 → FAILURE（Service 转发 2303，前端不无限 loading）。</p>
     */
    private Void consumeStream(ClientHttpResponse response, AtomicBoolean cancelled,
                               Consumer<AgentStreamEvent> consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String requestId = null;
            boolean completed = false;
            while (!completed && (cancelled == null || !cancelled.get())) {
                SseFrame frame = readNextFrame(reader);
                if (frame == null) {
                    break; // 流尾（EOF）：上游异常结束或未发结束帧
                }
                logSseFrame(frame);
                JsonNode dataNode;
                try {
                    dataNode = objectMapper.readTree(frame.data);
                } catch (Exception e) {
                    if ("[DONE]".equals(frame.data.trim())) {
                        // OpenAI 系 [DONE] 结束标记
                        log.info("Real 百宝箱 SSE 结束标记: event={}, data=[DONE]", frame.event);
                        consumer.accept(AgentStreamEvent.complete(requestId));
                        completed = true;
                        break;
                    }
                    consumer.accept(AgentStreamEvent.failure("SSE data 非合法 JSON"));
                    return null;
                }
                if (isTerminalFrame(frame, dataNode)) {
                    log.info("Real 百宝箱 SSE 结束帧: event={}, type={}, status={}",
                            frame.event, dataNode.path("type").asText(""), dataNode.path("status").asText(""));
                    consumer.accept(AgentStreamEvent.complete(requestId));
                    completed = true;
                    break;
                }
                String type = dataNode.path("type").asText("");
                if ("header".equals(type)) {
                    requestId = extractPayloadField(dataNode, "requestId");
                    if (requestId == null) {
                        requestId = dataNode.path("conversation_id").asText(null);
                    }
                    consumer.accept(AgentStreamEvent.header(requestId));
                    log.info("Real 百宝箱 SSE 映射 HEADER, requestIdLen={}", requestId == null ? 0 : requestId.length());
                    continue;
                }
                if ("chunk".equals(type)) {
                    String text = extractPayloadField(dataNode, "text");
                    if (text != null) {
                        consumer.accept(AgentStreamEvent.content(text));
                        log.info("Real 百宝箱 SSE 映射 CONTENT, len={}", text.length());
                    }
                    continue;
                }
                // 未知类型帧：尝试取顶层文本增量（兼容 conversation.message.delta 的 delta/content/text）
                String delta = topLevelText(dataNode);
                if (delta != null) {
                    consumer.accept(AgentStreamEvent.content(delta));
                    log.info("Real 百宝箱 SSE 映射 CONTENT(顶层字段), len={}", delta.length());
                    continue;
                }
                // meta / thinking / 心跳 / 未知帧：直接丢弃，不记录、不保存、不透传
                log.debug("Real 百宝箱 SSE 忽略帧: event={}, type={}", frame.event, type);
            }
            if (!completed) {
                log.warn("Real 百宝箱 SSE 流 EOF 未收到结束帧");
                consumer.accept(AgentStreamEvent.failure("SSE 流未收到结束帧"));
            }
        } catch (IOException e) {
            log.warn("Real 百宝箱 SSE 流读取异常", e);
            consumer.accept(AgentStreamEvent.failure("SSE 流读取失败"));
        }
        return null;
    }

    /** 结束帧判定：event/data.type/内嵌 event 命中结束语义，或 status=completed/done */
    private boolean isTerminalFrame(SseFrame frame, JsonNode dataNode) {
        if (isTerminalEventName(frame.event)) {
            return true;
        }
        if (isTerminalEventName(dataNode.path("type").asText(""))) {
            return true;
        }
        String status = dataNode.path("status").asText("");
        if ("completed".equals(status) || "done".equals(status)) {
            return true;
        }
        return isTerminalEventName(dataNode.path("event").asText(""));
    }

    /** 结束语义事件名：end/done/completed/complete/finish/stop 或 *.completed/*.done/*.end */
    private boolean isTerminalEventName(String event) {
        if (event == null) {
            return false;
        }
        String e = event.trim().toLowerCase();
        return TERMINAL_EVENTS.contains(e)
                || e.endsWith(".completed") || e.endsWith(".done") || e.endsWith(".end");
    }

    /** 取未知类型帧的顶层文本增量（delta/content/text），兼容蚂蚁百宝箱 conversation.message.delta */
    private String topLevelText(JsonNode node) {
        for (String field : new String[]{"delta", "content", "text"}) {
            String v = node.path(field).asText(null);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /** 诊断日志：上游 event 名 + data 长度 + 顶层字段名（脱敏，不落 Token/简历/题目正文） */
    private void logSseFrame(SseFrame frame) {
        try {
            JsonNode node = objectMapper.readTree(frame.data);
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            log.info("Real 百宝箱 SSE 帧: event={}, dataLen={}, topKeys={}", frame.event, frame.data.length(), keys);
        } catch (Exception e) {
            log.info("Real 百宝箱 SSE 帧: event={}, dataLen={}, data=<非JSON>", frame.event, frame.data.length());
        }
    }

    /** 关闭响应流（忽略关闭异常）：超时主动断开阻塞读取、释放线程
     *  <p>Spring 的 {@code ClientHttpResponse} 重定义 {@code close()} 不声明 {@code throws IOException}，
     *  故以 {@code Exception} 兜底吞掉一切关闭异常（连接已关闭/不可关闭均属正常）。</p> */
    private static void closeResponse(ClientHttpResponse resp) {
        if (resp == null) {
            return;
        }
        try {
            resp.close();
        } catch (Exception ignored) {
            // 连接已关闭/不可关闭
        }
    }

    // ==================== 请求构造 ====================

    /** 构造请求头：Authorization 原值（不 Bearer 包装）+ application/json；流式额外接受 text/event-stream */
    private HttpHeaders buildHeaders(boolean sse) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        if (sse) {
            headers.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));
        }
        return headers;
    }

    /** 构造 /api/chat 请求体 */
    private Map<String, Object> buildPayload(String appId, String query, String userId, boolean stream) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("appId", appId);
        root.put("query", query);
        root.put("userId", userId);
        root.put("stream", stream);
        return root;
    }

    // ==================== 非流式解析（JD，stream=false） ====================

    /**
     * 提取非流式响应中的模型文本
     * <p>成功标志：根级 {@code errorCode=="0"} 且 {@code success==true}；
     * 模型文本 {@code data.result[]} 中 {@code mediaType=text} 的 {@code chunk}。</p>
     */
    private String extractNonStreamResult(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalStateException("百宝箱 JD 解析响应为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("百宝箱 JD 解析响应非合法 JSON", e);
        }
        if (!"0".equals(root.path("errorCode").asText())
                || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("百宝箱 JD 解析失败: errorCode=" + root.path("errorCode").asText());
        }
        JsonNode result = root.path("data").path("result");
        if (!result.isArray()) {
            throw new IllegalStateException("百宝箱 JD 解析响应缺少 data.result 数组");
        }
        for (JsonNode item : result) {
            if ("text".equals(item.path("mediaType").asText()) && item.hasNonNull("chunk")) {
                return item.path("chunk").asText();
            }
        }
        throw new IllegalStateException("百宝箱 JD 解析响应缺少 text 类型的 chunk");
    }

    // ==================== 流式帧解析（面试，stream=true） ====================

    /** 单条 SSE 帧（event + data 原文） */
    private static class SseFrame {
        final String event;
        final String data;

        SseFrame(String event, String data) {
            this.event = event;
            this.data = data;
        }
    }

    /** 逐行读取一条 SSE 帧（event:/data:，空行结束；流尾返回 null） */
    private SseFrame readNextFrame(BufferedReader reader) throws IOException {
        String event = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data.length() > 0) {
                    return new SseFrame(event, data.toString());
                }
                continue;
            }
            if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                data.append(line.substring("data:".length()).trim());
            }
        }
        // 流尾：最后一段无空行终止的 data 也返回
        if (data.length() > 0) {
            return new SseFrame(event, data.toString());
        }
        return null;
    }

    /**
     * 二次解析外层 data 的 payload 字段
     * <p>{@code data} 为 {@code {type, payload(JSON字符串)}}，payload 需再 {@code JSON.parse} 后取值。</p>
     */
    private String extractPayloadField(JsonNode dataNode, String field) {
        try {
            String payloadJson = dataNode.path("payload").asText(null);
            if (payloadJson == null || payloadJson.isEmpty()) {
                return null;
            }
            JsonNode payload = objectMapper.readTree(payloadJson);
            String val = payload.path(field).asText(null);
            return val == null ? null : val;
        } catch (Exception e) {
            return null;
        }
    }
}

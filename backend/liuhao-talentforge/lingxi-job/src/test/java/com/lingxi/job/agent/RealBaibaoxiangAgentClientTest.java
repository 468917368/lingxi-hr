package com.lingxi.job.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.job.agent.impl.RealBaibaoxiangAgentClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real 百宝箱客户端 /api/chat 协议单测
 * <p>用 JDK 内置 HttpServer 起本地端口模拟 {@code /api/chat}，验证请求构造（POST/路径/Authorization
 * 原值/body 字段/无 A2A 字段）与响应解析（非流式 {@code data.result[0].chunk}、流式 header/chunk/end、
 * meta/thinking 丢弃、FAILURE 隔离）。纯单测，不依赖 Spring 装配。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
class RealBaibaoxiangAgentClientTest {

    private static final String JD_PARSE_JSON =
            "{\"jobType\":\"JAVA_BACKEND\","
                    + "\"coreSkills\":[{\"name\":\"Java\",\"level\":\"3\",\"required\":true}],"
                    + "\"softSkills\":[{\"name\":\"沟通\",\"importance\":\"3\"}],"
                    + "\"minExperienceYears\":3,\"educationRequirement\":\"BACHELOR\","
                    + "\"salary\":{\"currency\":\"CNY\",\"period\":\"MONTH\",\"minAmount\":100000,\"maxAmount\":200000},"
                    + "\"jdSummary\":\"负责后端开发\",\"warnings\":[]}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private String lastMethod;
    private String lastPath;
    private String lastAuth;
    private String lastBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** 构造客户端（baseUrl 指向本地测试服务器，token 用虚构值，流式总超时 10s） */
    private RealBaibaoxiangAgentClient client() {
        return client(10000L);
    }

    /** 指定流式总超时（毫秒）：超时用例用小值，正常流用默认 */
    private RealBaibaoxiangAgentClient client(long streamTimeoutMs) {
        return new RealBaibaoxiangAgentClient(objectMapper, new RestTemplateBuilder(),
                "http://localhost:" + server.getAddress().getPort(),
                "test-authorization", "test-hmac-key",
                "test-jd-app", "test-interview-app", 3000, 30000, streamTimeoutMs, 10);
    }

    /** 注册 handler：记录请求信息并按 contentType/status/body 响应 */
    private void register(String contentType, int status, String body) {
        server.createContext("/api/chat", exchange -> {
            capture(exchange);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private void capture(HttpExchange exchange) throws IOException {
        lastMethod = exchange.getRequestMethod();
        lastPath = exchange.getRequestURI().getPath();
        lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
        lastBody = readAll(exchange.getRequestBody());
    }

    /** JDK8 兼容的流读取 */
    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ==================== 请求构造断言 ====================

    private void assertRequestShape(String appId, boolean stream) {
        assertEquals("POST", lastMethod, "应为 POST");
        assertEquals("/api/chat", lastPath, "应为 /api/chat");
        assertEquals("test-authorization", lastAuth, "Authorization 应为配置原值（不 Bearer 包装）");
        assertTrue(lastBody.contains("\"appId\":\"" + appId + "\""), "body 应含 appId: " + lastBody);
        assertTrue(lastBody.contains("\"query\":\""), "body 应含 query");
        assertTrue(lastBody.contains("\"userId\":\""), "body 应含 userId");
        assertTrue(lastBody.contains("\"stream\":" + stream), "body 应含 stream=" + stream);
        assertFalse(lastBody.contains("jsonrpc"), "body 不应含 jsonrpc");
        assertFalse(lastBody.contains("message/stream"), "body 不应含 message/stream");
        assertFalse(lastBody.contains("/chat/a2a"), "body 不应含 /chat/a2a");
    }

    // ==================== 非流式（JD，stream=false） ====================

    @Test
    void parseJd_success_extractsTextChunk() throws Exception {
        // chunk 值是把 JD_PARSE_JSON 作为 JSON 字符串序列化（转义双引号）
        String chunkJson = objectMapper.writeValueAsString(JD_PARSE_JSON);
        register("application/json", 200,
                "{\"errorCode\":\"0\",\"success\":true,"
                        + "\"data\":{\"result\":[{\"mediaType\":\"text\",\"chunk\":" + chunkJson + "}]}}");
        String result = client().parseJd("脱敏 JD", "mock-user");
        assertEquals(JD_PARSE_JSON, result, "应返回 data.result[0].chunk");
        assertRequestShape("test-jd-app", false);
    }

    @Test
    void parseJd_errorCodeNotZero_throws() throws Exception {
        register("application/json", 200, "{\"errorCode\":\"1\",\"success\":false,\"data\":{}}");
        assertThrows(IllegalStateException.class, () -> client().parseJd("x", "mock-user"));
    }

    @Test
    void parseJd_missingTextChunk_throws() throws Exception {
        register("application/json", 200,
                "{\"errorCode\":\"0\",\"success\":true,\"data\":{\"result\":[{\"mediaType\":\"image\"}]}}");
        assertThrows(IllegalStateException.class, () -> client().parseJd("x", "mock-user"));
    }

    // ==================== 流式（面试，stream=true） ====================

    /** 构造一条 SSE 帧字符串 */
    private String sseFrame(String event, String dataJson) {
        return "event: " + event + "\n" + "data: " + dataJson + "\n\n";
    }

    /** 构造外层 data：{type, payload(JSON字符串)} */
    private String outerData(String type, String payloadJson) throws Exception {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.put("payload", payloadJson);
        return objectMapper.writeValueAsString(node);
    }

    private String payloadJson(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void streamInterview_headerChunkEnd_mapsInternalEvents() throws Exception {
        Map<String, Object> headerPayload = new LinkedHashMap<>();
        headerPayload.put("requestId", "req-1");
        headerPayload.put("sessionId", "s-1");
        headerPayload.put("mediaType", "text");
        String text = "{\"questions\":[{\"content\":\"q1\",\"type\":\"BASIC\",\"difficulty\":\"MEDIUM\"}]}";
        Map<String, Object> chunkPayload = new LinkedHashMap<>();
        chunkPayload.put("text", text);

        StringBuilder sse = new StringBuilder();
        sse.append(sseFrame("message", outerData("header", payloadJson(headerPayload))));
        sse.append(sseFrame("message", outerData("chunk", payloadJson(chunkPayload))));
        sse.append(sseFrame("end", outerData("end", null)));
        register("text/event-stream", 200, sse.toString());

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("prompt", "mock-user", new AtomicBoolean(false), events::add);

        assertEquals(3, events.size(), "应收到 HEADER/CONTENT/COMPLETE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.HEADER, events.get(0).getType());
        assertEquals(AgentStreamEvent.Type.CONTENT, events.get(1).getType());
        assertEquals(text, events.get(1).getContent(), "CONTENT 应为 chunk.payload.text");
        assertEquals(AgentStreamEvent.Type.COMPLETE, events.get(2).getType());
        assertRequestShape("test-interview-app", true);
    }

    @Test
    void streamInterview_metaAndThinking_dropped() throws Exception {
        Map<String, Object> headerPayload = new LinkedHashMap<>();
        headerPayload.put("requestId", "req-1");
        Map<String, Object> chunkPayload = new LinkedHashMap<>();
        chunkPayload.put("text", "{\"questions\":[]}");
        Map<String, Object> metaPayload = new LinkedHashMap<>();
        metaPayload.put("usage", "tokens");

        StringBuilder sse = new StringBuilder();
        sse.append(sseFrame("message", outerData("meta", payloadJson(metaPayload))));
        sse.append(sseFrame("message", outerData("header", payloadJson(headerPayload))));
        sse.append(sseFrame("message", outerData("thinking", payloadJson(metaPayload))));
        sse.append(sseFrame("message", outerData("chunk", payloadJson(chunkPayload))));
        sse.append(sseFrame("end", outerData("end", null)));
        register("text/event-stream", 200, sse.toString());

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("prompt", "mock-user", new AtomicBoolean(false), events::add);

        // meta/thinking 帧被丢弃，不产生事件
        assertEquals(3, events.size(), "meta/thinking 应被丢弃，仅 HEADER/CONTENT/COMPLETE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.HEADER, events.get(0).getType());
        assertEquals(AgentStreamEvent.Type.CONTENT, events.get(1).getType());
        assertEquals(AgentStreamEvent.Type.COMPLETE, events.get(2).getType());
    }

    @Test
    void streamInterview_payloadNotJson_failure() throws Exception {
        // data 行外层是合法 JSON，但 payload 值非 JSON 字符串 → extractPayloadField 返回 null，
        // 无 CONTENT；流尾无 end → FAILURE
        StringBuilder sse = new StringBuilder();
        sse.append("event: message\n")
                .append("data: {\"type\":\"chunk\",\"payload\":\"not-json\"}\n\n");
        register("text/event-stream", 200, sse.toString());

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("prompt", "mock-user", new AtomicBoolean(false), events::add);

        assertEquals(1, events.size(), "payload 非 JSON 且流尾无 end → FAILURE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.FAILURE, events.get(0).getType());
    }

    @Test
    void streamInterview_http500_failure() throws Exception {
        server.createContext("/api/chat", exchange -> {
            capture(exchange);
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("prompt", "mock-user", new AtomicBoolean(false), events::add);
        assertEquals(1, events.size(), "非 2xx 应产生 FAILURE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.FAILURE, events.get(0).getType());
    }

    @Test
    void streamInterview_streamCutBeforeEnd_failure() throws Exception {
        // 声明 Content-Length 大于实际写入 → 客户端读流 EOF → IOException → FAILURE
        server.createContext("/api/chat", exchange -> {
            capture(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write(
                    "event: message\ndata: {\"type\":\"header\"}".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
        });
        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("prompt", "mock-user", new AtomicBoolean(false), events::add);
        assertFalse(events.isEmpty(), "流中断应产生 FAILURE");
        assertEquals(AgentStreamEvent.Type.FAILURE, events.get(events.size() - 1).getType(),
                "最后一个事件应为 FAILURE，实际: " + events);
    }

    // ==================== 流式结束帧识别（真实百宝箱帧夹具，脱敏） ====================

    /** 脱敏真实帧夹具：header → chunk(3 题 PROJECT/BOUNDARY/COMPREHENSIVE) → event:done 结束帧 */
    @Test
    void streamInterview_realFrameFixture_doneEnding_completes() throws Exception {
        String sse = sseFrame("message", outerData("header", payloadJson(headerPayload("req-real-1"))))
                + sseFrame("message", outerData("chunk", payloadJson(chunkMap(threeQuestionJson()))))
                + sseFrame("done", outerData("done", null));
        register("text/event-stream", 200, sse);

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("prompt", "mock-user", new AtomicBoolean(false), events::add);

        assertEquals(3, events.size(), "应 HEADER/CONTENT/COMPLETE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.HEADER, events.get(0).getType());
        assertEquals(AgentStreamEvent.Type.CONTENT, events.get(1).getType());
        assertEquals(threeQuestionJson(), events.get(1).getContent(), "CONTENT 应为真实 3 题 JSON");
        assertEquals(AgentStreamEvent.Type.COMPLETE, events.get(2).getType(),
                "event:done 应识别为正常结束 → COMPLETE");
    }

    /** 结束帧变体：data.status=completed（蚂蚁百宝箱 conversation.chat.completed 语义） */
    @Test
    void streamInterview_statusCompleted_completes() throws Exception {
        String sse = sseFrame("message", outerData("header", payloadJson(headerPayload("req-2"))))
                + sseFrame("message", "{\"status\":\"completed\",\"conversation_id\":\"c-1\"}");
        register("text/event-stream", 200, sse);

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("p", "mock-user", new AtomicBoolean(false), events::add);

        assertEquals(2, events.size(), "应 HEADER/COMPLETE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.HEADER, events.get(0).getType());
        assertEquals(AgentStreamEvent.Type.COMPLETE, events.get(1).getType(),
                "data.status=completed 应识别为结束 → COMPLETE");
    }

    /** 结束标记变体：data=[DONE]（OpenAI 系，非合法 JSON 的纯文本结束标记） */
    @Test
    void streamInterview_doneBracket_completes() throws Exception {
        String sse = sseFrame("message", outerData("header", payloadJson(headerPayload("req-3"))))
                + sseFrame("message", "[DONE]");
        register("text/event-stream", 200, sse);

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("p", "mock-user", new AtomicBoolean(false), events::add);

        assertEquals(2, events.size(), "应 HEADER/COMPLETE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.COMPLETE, events.get(1).getType(),
                "data=[DONE] 应识别为结束 → COMPLETE");
    }

    /** 蚂蚁百宝箱格式：conversation.message.delta 顶层 delta 文本 + conversation.chat.completed */
    @Test
    void streamInterview_conversationFormat_deltaContentAndCompleted() throws Exception {
        String delta = objectMapper.writeValueAsString(threeQuestionJson());
        String sse = sseFrame("conversation.message.delta", "{\"delta\":" + delta + ",\"status\":\"in_progress\"}")
                + sseFrame("conversation.chat.completed", "{\"status\":\"completed\"}");
        register("text/event-stream", 200, sse);

        List<AgentStreamEvent> events = new ArrayList<>();
        client().streamInterview("p", "mock-user", new AtomicBoolean(false), events::add);

        assertEquals(2, events.size(), "应 CONTENT/COMPLETE，实际: " + events);
        assertEquals(AgentStreamEvent.Type.CONTENT, events.get(0).getType(),
                "delta 顶层字段应映射为 CONTENT");
        assertEquals(threeQuestionJson(), events.get(0).getContent());
        assertEquals(AgentStreamEvent.Type.COMPLETE, events.get(1).getType(),
                "conversation.chat.completed 应识别为结束 → COMPLETE");
    }

    /** 上游发完数据但不发结束帧且连接保持 → 总超时 → FAILURE（streamInterview 必有界返回） */
    @Test
    void streamInterview_noEndFrame_keepAlive_timeout_failure() throws Exception {
        // 帧在 lambda 外构造（outerData/payloadJson 抛 checked Exception，HttpHandler 无法声明）
        String headerFrame = sseFrame("message", outerData("header", payloadJson(headerPayload("req-t"))));
        String chunkFrame = sseFrame("message", outerData("chunk", payloadJson(chunkMap(threeQuestionJson()))));
        server.createContext("/api/chat", exchange -> {
            capture(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write(headerFrame.getBytes(StandardCharsets.UTF_8));
            os.write(chunkFrame.getBytes(StandardCharsets.UTF_8));
            os.flush();
            try {
                Thread.sleep(5000); // 保持连接、不发结束帧 → 客户端总超时兜底
            } catch (InterruptedException ignored) {
                // 测试结束 server.stop 打断
            }
        });

        List<AgentStreamEvent> events = new ArrayList<>();
        client(1000L).streamInterview("p", "mock-user", new AtomicBoolean(false), events::add);

        // HEADER/CONTENT 为过程事件已直通回调，最后一个必须是 FAILURE（超时兜底，非无限阻塞）
        assertFalse(events.isEmpty(), "无结束帧且连接保持 → 应产生 FAILURE");
        assertEquals(AgentStreamEvent.Type.FAILURE, events.get(events.size() - 1).getType(),
                "总超时应回调 FAILURE（Service 转发 2303，前端不无限 loading），实际: " + events);
        assertTrue(events.stream().noneMatch(e -> e.getType() == AgentStreamEvent.Type.COMPLETE),
                "无结束帧时不应回调 COMPLETE");
    }

    // ==================== 夹具辅助 ====================

    /** header 帧 payload */
    private Map<String, Object> headerPayload(String requestId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", requestId);
        return m;
    }

    /** chunk 帧 payload */
    private Map<String, Object> chunkMap(String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        return m;
    }

    /** 脱敏真实 result JSON：3 题 PROJECT/BOUNDARY/COMPREHENSIVE（HARD），不含敏感内容 */
    private String threeQuestionJson() {
        return "{\"questions\":["
                + "{\"type\":\"PROJECT\",\"difficulty\":\"HARD\",\"content\":\"如何设计新用户首单转化策略（脱敏）\"},"
                + "{\"type\":\"BOUNDARY\",\"difficulty\":\"HARD\",\"content\":\"增长评估与系统改造的权衡（脱敏）\"},"
                + "{\"type\":\"COMPREHENSIVE\",\"difficulty\":\"HARD\",\"content\":\"设计用户增长三阶段策略（脱敏）\"}"
                + "]}";
    }
}

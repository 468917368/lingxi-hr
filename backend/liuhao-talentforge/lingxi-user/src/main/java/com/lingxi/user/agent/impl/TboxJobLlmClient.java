package com.lingxi.user.agent.impl;

import cn.tbox.sdk.TboxClient;
import cn.tbox.sdk.model.request.ChatRequest;
import com.lingxi.user.agent.JobAgentLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 百宝箱 tboxsdk 对话型接口客户端（真实模式）
 * <p>
 * 通过 tboxsdk 调用百宝箱 LLM API，支持流式和非流式两种模式。
 * 配置 job-agent.mock=false 时激活（mock=true 时使用 MockJobLlmClient）。
 * </p>
 * <p>
 * 核心方法：
 * - generate(): 一次性返回完整文本（同步阻塞）
 * - generateStream(): 流式返回，每收到一个 chunk 就回调（用于 SSE 实时推送）
 * </p>
 * <p>
 * 超时控制：通过 Future.get(timeoutMs) 实现，超时后取消任务并抛异常。
 * 线程池：CachedThreadPool，daemon 线程，不阻塞 JVM 关闭。
 * </p>
 * <p>
 * 返回格式处理：
 * - Iterable（流式）：逐个 chunk 解析，区分 thinking/reasoning 和正常回答
 * - Map（非流式）：递归提取 text/content 字段
 * - String（兜底）：直接使用
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j                                          // 自动生成 log 对象
@Component                                      // Spring Bean
@RequiredArgsConstructor                        // 自动生成构造函数（注入 TboxClient）
@ConditionalOnProperty(name = "job-agent.mock", havingValue = "false")  // mock=false 时才激活
public class TboxJobLlmClient implements JobAgentLlmClient {

    /** 百宝箱 SDK 客户端（由 Spring 自动注入） */
    private final TboxClient tboxClient;

    /**
     * 异步线程池（用于超时控制）
     * <p>
     * CachedThreadPool：按需创建线程，空闲 60s 回收。
     * daemon 线程：不阻塞 JVM 关闭（main 线程结束后自动退出）。
     * 线程名 "tbox-job-llm"：方便日志排查。
     * </p>
     * <p>
     * 为什么不用普通同步调用？
     * 因为 tboxClient.chat() 没有超时参数，需要通过 Future.get(timeout) 实现超时控制。
     * </p>
     */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tbox-job-llm");
        t.setDaemon(true);  // daemon 线程，JVM 退出时自动结束
        return t;
    });

    /**
     * 非流式调用：一次性返回完整文本
     * <p>
     * 用途：意图分类（classifyAndExtract）、降级场景
     * 特点：等待完整响应后返回，延迟较高但结果完整
     * </p>
     *
     * @param appId     百宝箱应用ID
     * @param query     发给 LLM 的 prompt（含工具结果 + 用户问题）
     * @param userId    用户标识（百宝箱会话隔离）
     * @param timeoutMs 超时毫秒数
     * @return LLM 生成的完整文本
     * @throws RuntimeException 超时或调用失败
     */
    @Override
    public String generate(String appId, String query, String userId, long timeoutMs) {
        Future<String> future = EXECUTOR.submit(() -> doGenerate(appId, query, userId, null));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("百宝箱 LLM 调用超时: appId={}", appId);
            throw new RuntimeException("AI 服务调用超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI 服务调用被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("百宝箱 LLM 调用失败: appId={}", appId, cause);
            throw new RuntimeException("AI 服务调用失败: " + (cause != null ? cause.getMessage() : "未知错误"));
        }
    }

    /**
     * 流式调用：每收到一个 chunk 就回调，用于 SSE 实时推送
     * <p>
     * 用途：主对话流程（JobAgentService.chat）
     * 特点：边生成边推送，用户感知快（首字延迟低）
     * </p>
     * <p>
     * 回调逻辑：
     * - thinking/reasoning 类型的 chunk → 加 [THINKING] 前缀（前端可折叠显示）
     * - 正常回答的 chunk → 直接回调（前端逐字显示）
     * </p>
     *
     * @param appId     百宝箱应用ID
     * @param query     发给 LLM 的 prompt
     * @param userId    用户标识
     * @param timeoutMs 超时毫秒数
     * @param onChunk   每收到一段文本的回调（chunk → SSE 推送给前端）
     * @return LLM 生成的完整文本（所有 chunk 拼接）
     * @throws RuntimeException 超时或调用失败
     */
    @Override
    public String generateStream(String appId, String query, String userId,
                                  long timeoutMs, ChunkCallback onChunk) {
        Future<String> future = EXECUTOR.submit(() -> doGenerate(appId, query, userId, onChunk));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("百宝箱 LLM 流式调用超时: appId={}", appId);
            throw new RuntimeException("AI 服务调用超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI 服务调用被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("百宝箱 LLM 流式调用失败: appId={}", appId, cause);
            throw new RuntimeException("AI 服务调用失败: " + (cause != null ? cause.getMessage() : "未知错误"));
        }
    }

    /**
     * 实际调用百宝箱 API（流式/非流式共用）
     * <p>
     * 返回格式处理（按优先级）：
     * 1. Iterable（流式返回）：逐个 chunk 解析，区分 thinking 和正常回答
     * 2. Map（非流式返回）：递归提取 text/content/chunk 字段
     * 3. String（兜底）：直接使用
     * 4. 其他类型：toString() 兜底
     * </p>
     *
     * @param appId    百宝箱应用ID
     * @param query    prompt 文本
     * @param userId   用户标识
     * @param onChunk  chunk 回调（null 表示非流式模式）
     * @return 完整文本
     */
    private String doGenerate(String appId, String query, String userId, ChunkCallback onChunk) {
        try {
            ChatRequest request = new ChatRequest(appId, query, userId);
            Object result = tboxClient.chat(request);
            StringBuilder sb = new StringBuilder();

            log.info("百宝箱返回类型: {}, class: {}", result != null ? "非null" : "null",
                     result != null ? result.getClass().getName() : "null");

            if (result instanceof Iterable) {
                log.info("百宝箱返回 Iterable，开始遍历 chunks");
                for (Object item : (Iterable<?>) result) {
                    if (item instanceof Map) {
                        Map<?, ?> chunkMap = (Map<?, ?>) item;
                        String[] extracted = extractTextWithThinking(chunkMap);
                        String lane = extracted[0];
                        String text = extracted[1];

                        if (text != null && !text.isEmpty()) {
                            if ("thinking".equals(lane) || "reasoning".equals(lane)) {
                                // 思考过程：加特殊前缀，前端可折叠显示
                                log.debug("收到思考内容: {}", text);
                                if (onChunk != null) {
                                    onChunk.onChunk("[THINKING]" + text);
                                }
                            } else {
                                // 正常回答
                                sb.append(text);
                                log.debug("收到回答 chunk: {}", text);
                                if (onChunk != null) {
                                    onChunk.onChunk(text);
                                }
                            }
                        }
                    }
                }
            } else if (result instanceof Map) {
                // 非流式返回，一次性提取文本
                log.info("百宝箱返回 Map（非流式），一次性提取");
                extractText((Map<?, ?>) result, sb);
                if (onChunk != null && sb.length() > 0) {
                    onChunk.onChunk(sb.toString());
                }
            } else if (result instanceof String) {
                // 直接返回字符串
                log.info("百宝箱返回 String（非流式）");
                sb.append((String) result);
                if (onChunk != null && sb.length() > 0) {
                    onChunk.onChunk(sb.toString());
                }
            } else {
                log.warn("百宝箱返回未知类型: {}", result != null ? result.getClass() : "null");
                // 兜底：toString
                if (result != null) {
                    sb.append(result.toString());
                    if (onChunk != null && sb.length() > 0) {
                        onChunk.onChunk(sb.toString());
                    }
                }
            }

            log.info("百宝箱最终结果长度: {}", sb.length());
            return sb.toString();
        } catch (Exception e) {
            log.error("百宝箱 chat 调用失败", e);
            throw new RuntimeException("百宝箱 chat 调用失败", e);
        }
    }

    /**
     * 递归从 chunk Map 提取文本片段（非流式模式）
     * <p>
     * 遍历 Map 的所有 key，找到 text/content 字段就拼接。
     * 嵌套 Map 或 List 递归处理。
     * </p>
     *
     * @param map  百宝箱返回的 Map（可能嵌套多层）
     * @param sb   拼接结果的 StringBuilder
     */
    private void extractText(Map<?, ?> map, StringBuilder sb) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String && isTextKey(entry.getKey())) {
                sb.append((String) value);
            } else if (value instanceof Map) {
                extractText((Map<?, ?>) value, sb);
            } else if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) {
                    if (item instanceof Map) {
                        extractText((Map<?, ?>) item, sb);
                    } else if (item instanceof String && isTextKey(entry.getKey())) {
                        sb.append((String) item);
                    }
                }
            }
        }
    }

    /**
     * 从 chunk Map 提取文本和思考内容（流式模式）
     * <p>
     * 百宝箱返回的 chunk 可能包含：
     * - lane="thinking"/"reasoning"：思考过程（前端可折叠显示）
     * - lane=null/其他：正常回答（前端逐字显示）
     * </p>
     * <p>
     * 提取逻辑：先找 lane 字段确定类型，再从 chunk/text/content 字段提取文本。
     * 嵌套 Map 递归查找。
     * </p>
     *
     * @param map 百宝箱返回的 chunk Map
     * @return [thinkingText, answerText]，任一为 null 表示该类型无内容
     */
    private String[] extractTextWithThinking(Map<?, ?> map) {
        String lane = null;
        String text = null;

        // 提取 lane 字段
        Object laneObj = map.get("lane");
        if (laneObj instanceof String) {
            lane = (String) laneObj;
        }

        // 提取文本
        for (String key : new String[]{"chunk", "text", "content"}) {
            Object val = map.get(key);
            if (val instanceof String && !((String) val).isEmpty()) {
                text = (String) val;
                break;
            }
        }

        // 递归查找嵌套
        if (text == null) {
            for (Object value : map.values()) {
                if (value instanceof Map) {
                    String[] nested = extractTextWithThinking((Map<?, ?>) value);
                    if (nested[1] != null) {
                        if (lane == null) lane = nested[0];
                        text = nested[1];
                        break;
                    }
                }
            }
        }

        return new String[]{lane, text};
    }

    /**
     * 判断 Map 的 key 是否为文本内容字段
     * <p>
     * 百宝箱返回的 chunk Map 中，文本内容可能在 chunk/text/content 字段里。
     * </p>
     */
    private boolean isTextKey(Object key) {
        if (key == null) return false;
        String k = String.valueOf(key);
        return "chunk".equals(k) || "text".equals(k) || "content".equals(k);
    }
}

package com.lingxi.hr.agent.impl;

import cn.tbox.sdk.TboxClient;
import cn.tbox.sdk.core.exception.TboxClientConfigException;
import cn.tbox.sdk.core.exception.TboxHttpResponseException;
import cn.tbox.sdk.model.request.ChatRequest;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.hr.agent.LlmClient;
import com.lingxi.hr.exception.HrErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 百宝箱 tboxsdk 对话型接口客户端（真实模式）
 * <p>调用 {@code TboxClient.chat(ChatRequest)}（路由 /api/chat），流式 chunk 拼接为完整文本。</p>
 * <p>注意：SDK 0.0.10 的 {@code completion()} 走 /api/completion 路由不存在（ROUTE_NOT_FOUND），
 * 因此改用 chat（/api/chat），工作流参数通过 query 文本传入。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hr.agent.mock", havingValue = "false")
public class TboxLlmClient implements LlmClient {

    private final TboxClient tboxClient;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tbox-llm");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String generate(String appId, String query,
                           String userId, long timeoutMs) {
        Future<String> future = EXECUTOR.submit(() -> doGenerate(appId, query, userId));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("百宝箱 LLM 调用超时: appId={}, timeoutMs={}", appId, timeoutMs);
            throw new BusinessException(HrErrorCode.MOCK_GENERATE_FAILED.getErrorCode(), "AI 服务调用超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HrErrorCode.MOCK_GENERATE_FAILED.getErrorCode(), "AI 服务调用被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("百宝箱 LLM 调用失败: appId={}", appId, cause);
            throw new BusinessException(HrErrorCode.MOCK_GENERATE_FAILED.getErrorCode(),
                    "AI 服务调用失败: " + (cause != null ? cause.getMessage() : "未知错误"));
        }
    }

    private String doGenerate(String appId, String query, String userId) {
        ChatRequest request = new ChatRequest(appId, query, userId);
        Object result;
        try {
            result = tboxClient.chat(request);
        } catch (TboxClientConfigException | TboxHttpResponseException e) {
            throw new RuntimeException("百宝箱 chat 调用失败", e);
        }
        StringBuilder sb = new StringBuilder();
        if (result instanceof Iterable) {
            for (Object item : (Iterable<?>) result) {
                if (item instanceof Map) {
                    extractText((Map<?, ?>) item, sb);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 递归从 chunk Map 提取文本片段（chunk/text/content 键），拼接完整内容。
     * <p>百宝箱流式 chunk 结构：{requestId, result:[{chunk:"...", mediaType:"text/plain"}]} 或扁平 {chunk:"..."}。</p>
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

    private boolean isTextKey(Object key) {
        if (key == null) {
            return false;
        }
        String k = String.valueOf(key);
        return "chunk".equals(k) || "text".equals(k) || "content".equals(k);
    }
}

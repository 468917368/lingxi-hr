package com.lingxi.user.agent;

/**
 * LLM 客户端接口（封装百宝箱 /api/chat）
 *
 * @author 成员A
 * @since 2026-08-05
 */
public interface JobAgentLlmClient {

    /**
     * 调用百宝箱智能体/工作流，返回完整文本
     *
     * @param appId     应用 appId
     * @param query     用户问题文本（包含工具数据 + 用户消息）
     * @param userId    用户会话标识
     * @param timeoutMs 单次调用超时（ms）
     * @return 智能体返回的原始文本
     */
    String generate(String appId, String query, String userId, long timeoutMs);

    /**
     * 流式调用：每收到一个文本片段就回调，用于 SSE 实时推送
     *
     * @param appId     应用 appId
     * @param query     用户问题文本
     * @param userId    用户会话标识
     * @param timeoutMs 超时（ms）
     * @param onChunk   每收到一段文本的回调
     * @return 完整文本
     */
    default String generateStream(String appId, String query, String userId,
                                   long timeoutMs, ChunkCallback onChunk) {
        // 默认不支持流式，降级为一次性返回
        String result = generate(appId, query, userId, timeoutMs);
        if (onChunk != null) {
            onChunk.onChunk(result);
        }
        return result;
    }

    /**
     * 文本片段回调
     */
    @FunctionalInterface
    interface ChunkCallback {
        void onChunk(String text);
    }
}

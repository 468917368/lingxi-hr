package com.lingxi.hr.agent;

/**
 * LLM 客户端接口（封装百宝箱 tboxsdk 对话型接口 /api/chat）
 *
 * @author 成员D
 * @since 2026-08-04
 */
public interface LlmClient {

    /**
     * 调用百宝箱智能体/工作流，返回完整 JSON 文本（期望严格 JSON 字符串）。
     *
     * @param appId     应用 appId（出题/评分/报告，见 {@code BaibaoxiangProperties}）
     * @param query     用户问题文本（工作流从 query 中提取参数，见 {@code MockPromptBuilder}）
     * @param userId    用户会话标识（传 candidateId.toString()）
     * @param timeoutMs 单次调用超时（ms）
     * @return 智能体返回的原始文本
     */
    String generate(String appId, String query, String userId, long timeoutMs);
}

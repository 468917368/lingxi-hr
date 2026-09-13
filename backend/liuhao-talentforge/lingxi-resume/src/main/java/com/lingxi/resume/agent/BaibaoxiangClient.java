package com.lingxi.resume.agent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 百宝箱 A2A 智能体客户端（A2A 协议 0.3.0 / JSON-RPC 2.0 over HTTP SSE）
 *
 * <p>端点：POST https://o.tbox.cn/openapi/v1/chat/a2a/{agentId}
 * <p>认证：Authorization: Bearer {API-Key} + X-User-Id 头
 * <p>请求体：{jsonrpc:"2.0", method:"message/stream",
 *             params:{message:{role:"user", parts:[{kind:"text", text:"..."}]}}}
 * <p>响应：SSE 流，逐行返回 JSON-RPC 响应对象（A2A 事件）
 *
 * <p>当前实现：
 * <ul>
 *   <li>{@code resume.agent.mock=true} → {@code MockBaibaoxiangClient}（本地模拟 A2A 事件流）</li>
 *   <li>{@code resume.agent.mock=false} → {@code RealBaibaoxiangClient}（真实 HTTP 调百宝箱，待智能体配完后实现）</li>
 * </ul>
 *
 * <p>C 不感知工作流内部的工具调用过程（tool_call 发生在百宝箱工作流节点内部，A2A 事件中不可见）。
 *
 * @author 成员C
 * @since 2026-08-03
 */
public interface BaibaoxiangClient {

    /**
     * 向百宝箱发起流式对话（传 PDF URL，内部 Tika 提取文本后发送）
     *
     * @param pdfFileUrl 简历 PDF 的可访问 URL（MinIO presigned URL）
     * @param contextId  会话上下文 ID（传 resumeId.toString()）
     * @param cancelled  外部取消标志（客户端断开连接时置 true，中断 SSE 读取）
     * @return A2A 事件流迭代器（TextEvent / ProgressEvent / ArtifactEvent / StatusEvent）
     */
    Iterable<A2AEvent> chatA2A(String pdfFileUrl, String contextId, AtomicBoolean cancelled);

    /**
     * 按模式发起对话（阶段化解析：卡片先行返回，评分后台落库）
     *
     * @param pdfFileUrl 简历 PDF 的可访问 URL
     * @param mode       解析模式：
     *                   {@link #MODE_CARD} = 仅输出 card_structure（快速返回，SSE:final 推前端）；
     *                   {@link #MODE_SCORE} = 仅输出 personal + ability_model（后台落库）
     * @param contextId  会话上下文 ID
     * @param cancelled  取消标志
     * @return A2A 事件流迭代器
     */
    Iterable<A2AEvent> chatA2A(String pdfFileUrl, String mode, String contextId, AtomicBoolean cancelled);

    /** 卡片模式：仅输出 card_structure */
    String MODE_CARD = "CARD";

    /** 评分模式：仅输出 personal + ability_model */
    String MODE_SCORE = "SCORE";

    /** 诊断模式：职业匹配度分析 + Markdown 诊断报告 */
    String MODE_DIAGNOSIS = "DIAGNOSIS";

    /**
     * 向百宝箱发起纯文本对话（诊断等非 PDF 场景，不提取 PDF 文本）
     *
     * @param text      文本输入（诊断场景为简历上下文JSON）
     * @param mode      模式：MODE_DIAGNOSIS
     * @param contextId 上下文 ID
     * @param cancelled 取消标志
     * @return A2A 事件流
     */
    Iterable<A2AEvent> chatText(String text, String mode, String contextId, AtomicBoolean cancelled);

    /**
     * 获取最近一次对话的 conversationId（两阶段解析时复用上下文）
     */
    default String getLastConversationId() {
        return null;
    }

    /**
     * 设置当前会话 ID（SCORE 模式复用 CARD 阶段的上下文，避免重发简历文本）
     */
    default void setConversationId(String conversationId) {
    }

    /**
     * 获取指定简历的 Tika 提取原文（按 contextId=resumeId 存储；MD 兜底与规则引擎降级用）
     */
    default String getLastResumeText(String contextId) {
        return null;
    }
}

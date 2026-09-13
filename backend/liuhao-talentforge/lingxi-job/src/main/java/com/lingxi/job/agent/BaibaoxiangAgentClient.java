package com.lingxi.job.agent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 百宝箱智能体客户端抽象（阶段4 真实协议修订）
 * <p>
 * B 侧不直调大模型 API，百宝箱编排 Interview Agent，B 只做透传 SSE + Tool 提供。
 * 本接口封装对百宝箱的两类调用：
 * <ul>
 *   <li>{@link #parseJd}：JD 解析 → 画像草稿 JSON（{@code stream=false}）</li>
 *   <li>{@link #streamInterview}：Interview Agent 出题（{@code stream=true}），
 *   通过 {@link Consumer}{@code <AgentStreamEvent>} 逐帧回调内部事件</li>
 * </ul>
 * </p>
 * <p>
 * 双实现按 {@code ai.agent.mock} 切换（默认 true=Mock）：
 * <ul>
 *   <li>{@code ai.agent.mock=true} → {@code MockBaibaoxiangAgentClient}（本地模拟内部事件流，可测）</li>
 *   <li>{@code ai.agent.mock=false} → {@code RealBaibaoxiangAgentClient}（真实 /api/chat 出站）</li>
 * </ul>
 * </p>
 * <p>
 * <b>userId</b>：由 {@link BaibaoxiangUserIdProvider} 生成后<b>显式传入</b>，客户端不接收原始内部用户 ID。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
public interface BaibaoxiangAgentClient {

    /**
     * JD 解析：调用百宝箱 Agent，返回结构化画像 JSON 字符串（非流式）
     *
     * @param prompt 组装好的解析 Prompt（含 JSON Schema 与约束，JD 已脱敏）
     * @param userId 按 AppID 隔离的百宝箱伪标识（HMAC hex / mock 值）
     * @return 画像草稿 JSON（失败/超时由实现方抛异常）
     */
    String parseJd(String prompt, String userId);

    /**
     * Interview Agent 出题（流式）：请求百宝箱后逐帧回调内部事件
     *
     * @param prompt    组装好的出题 Prompt（含岗位画像/简历亮点/题库参考）
     * @param userId    按 AppID 隔离的百宝箱伪标识
     * @param cancelled 外部取消标志（客户端断开时置 true，中断读取）
     * @param consumer  内部事件消费者（HEADER/CONTENT/COMPLETE/FAILURE，阻塞式同步回调）
     */
    void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                         Consumer<AgentStreamEvent> consumer);
}

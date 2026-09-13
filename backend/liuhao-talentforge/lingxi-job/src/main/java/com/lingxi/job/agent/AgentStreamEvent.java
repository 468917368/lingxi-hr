package com.lingxi.job.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 百宝箱第三方流事件（协议隔离模型）
 * <p>
 * 仅表达项目内部消费 {@link #getType()} 四种语义，屏蔽第三方原始帧：
 * <ul>
 *   <li>{@code HEADER}：连接成功，仅携带诊断用 requestId</li>
 *   <li>{@code CONTENT}：模型输出增量文本（累积后为完整 JSON）</li>
 *   <li>{@code COMPLETE}：流正常结束</li>
 *   <li>{@code FAILURE}：协议/网络/payload 异常，携带 errorMessage</li>
 * </ul>
 * 不包含外部原始 {@code event}、原始 payload、{@code meta}、{@code thinking} 或 Token。
 * 前端最终事件（progress/result/done/error）由 {@link AgentSseEvent} 承载，本类与其无关。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
@AllArgsConstructor
public class AgentStreamEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内部事件类型（仅四种） */
    public enum Type {
        /** 连接成功，携带诊断用 requestId */
        HEADER,
        /** 模型输出增量文本（累积后为完整 JSON） */
        CONTENT,
        /** 流正常结束 */
        COMPLETE,
        /** 协议/网络/payload 异常 */
        FAILURE
    }

    /** 事件类型 */
    private Type type;

    /** 增量内容（仅 CONTENT 有值；HEADER/COMPLETE/FAILURE 为 null） */
    private String content;

    /** 诊断用请求 ID（HEADER/COMPLETE 携带，其余为 null） */
    private String requestId;

    /** 错误描述（仅 FAILURE 有值） */
    private String errorMessage;

    /** 连接成功事件（携带诊断用 requestId） */
    public static AgentStreamEvent header(String requestId) {
        return new AgentStreamEvent(Type.HEADER, null, requestId, null);
    }

    /** 模型输出增量事件 */
    public static AgentStreamEvent content(String content) {
        return new AgentStreamEvent(Type.CONTENT, content, null, null);
    }

    /** 流正常结束事件 */
    public static AgentStreamEvent complete(String requestId) {
        return new AgentStreamEvent(Type.COMPLETE, null, requestId, null);
    }

    /** 异常结束事件 */
    public static AgentStreamEvent failure(String errorMessage) {
        return new AgentStreamEvent(Type.FAILURE, null, null, errorMessage);
    }
}

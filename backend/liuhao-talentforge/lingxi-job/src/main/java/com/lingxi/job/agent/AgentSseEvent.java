package com.lingxi.job.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 前端 SSE 最终事件（项目内部最小事件载体）
 * <p>
 * event 取值：{@code progress}/{@code result}/{@code done}/{@code error}，
 * data 为对应事件的 JSON 字符串（前端可透传）。
 * </p>
 * <p>
 * 本类<b>仅承载项目最终事件，不再承载第三方原始帧</b>；百宝箱原始
 * event/payload/thinking/meta 由 {@link AgentStreamEvent} 隔离后消费。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
@AllArgsConstructor
public class AgentSseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** progress 事件名（阶段进度） */
    public static final String EVENT_PROGRESS = "progress";
    /** result 事件名（出题结果） */
    public static final String EVENT_RESULT = "result";
    /** done 事件名（正常结束） */
    public static final String EVENT_DONE = "done";
    /** error 事件名（异常结束） */
    public static final String EVENT_ERROR = "error";

    /** 事件名：progress/result/done/error */
    private String event;

    /** 事件数据 JSON 字符串 */
    private String data;
}

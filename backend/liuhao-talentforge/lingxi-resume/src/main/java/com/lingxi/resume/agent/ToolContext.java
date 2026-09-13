package com.lingxi.resume.agent;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行上下文
 *
 * <p>承载 Agent 工具调用所需的会话数据（简历 ID、Tika 提取的简历文本），
 * 由 {@link ResumeAgentService} 构造并传给 {@link ToolExecutor}。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Data
@Builder
public class ToolContext {

    /** 简历 ID */
    private Long resumeId;

    /** Tika 提取的简历纯文本 */
    private String tikaText;
}

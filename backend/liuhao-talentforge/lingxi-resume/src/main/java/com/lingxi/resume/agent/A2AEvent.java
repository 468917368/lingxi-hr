package com.lingxi.resume.agent;

import lombok.Getter;

import java.util.List;

/**
 * A2A 协议流式事件类型（百宝箱智能体平台，A2A 0.3.0）
 *
 * <p>替换 {@code BaibaoxiangChunk}。A2A 协议与 OpenAI function calling 不同：
 * <ul>
 *   <li>没有 {@code tool_call} 事件——工具调用发生在百宝箱工作流内部，C 不可见</li>
 *   <li>产物叫 {@code artifact} 不叫 {@code finish}——通过 {@link ArtifactEvent#isLastChunk} 判定终态</li>
 *   <li>有独立的状态事件 {@link StatusEvent}——标记任务生命周期</li>
 * </ul>
 *
 * <p>SSE 映射关系：
 * <pre>
 *   TextEvent       → SSE:thinking（LLM 思维链/进度文本）
 *   ProgressEvent   → SSE:progress（card_structure 章节级实时进度）
 *   ArtifactEvent   → SSE:final（lastChunk=true 时）
 *   StatusEvent     → 结束标记或 SSE:error（state=failed 时）
 * </pre>
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Getter
public abstract class A2AEvent {

    private A2AEvent() {
    }

    /** LLM 文本输出（thinking/推理文本），对应 A2A message event with text part */
    @Getter
    public static final class TextEvent extends A2AEvent {
        private final String content;

        public TextEvent(String content) {
            this.content = content;
        }
    }

    /**
     * 章节级进度（card_structure 流式生成过程中的实时进度）
     *
     * <p>LLM 输出 JSON 按 chunk 流式到达，本地实时扫描已闭合的 section 对象，
     * 每完成一个章节推一次本事件，前端据此逐张渲染卡片骨架（不阻塞主流程）。
     */
    @Getter
    public static final class ProgressEvent extends A2AEvent {
        /** 阶段：card（章节生成）/ score（能力评估） */
        private final String stage;
        /** 已完成章节数 */
        private final int sectionsDone;
        /** 已完成章节标题列表（累积全量） */
        private final List<String> titles;

        public ProgressEvent(String stage, int sectionsDone, List<String> titles) {
            this.stage = stage;
            this.sectionsDone = sectionsDone;
            this.titles = titles;
        }
    }

    /** 智能体产物更新，对应 A2A artifact-update event */
    @Getter
    public static final class ArtifactEvent extends A2AEvent {
        /** 产物ID */
        private final String artifactId;
        /** 产物内容（JSON 字符串：card_structure + ability_model + resume_md） */
        private final String content;
        /** 是否是最后一个分块（true 时取 content 作为 final 输出） */
        private final boolean lastChunk;

        public ArtifactEvent(String artifactId, String content, boolean lastChunk) {
            this.artifactId = artifactId;
            this.content = content;
            this.lastChunk = lastChunk;
        }
    }

    /** 任务状态更新，对应 A2A status-update event */
    @Getter
    public static final class StatusEvent extends A2AEvent {
        /** 状态：submitted / working / completed / failed / canceled */
        private final String state;
        /** 是否终态 */
        private final boolean isFinal;

        public StatusEvent(String state, boolean isFinal) {
            this.state = state;
            this.isFinal = isFinal;
        }
    }
}

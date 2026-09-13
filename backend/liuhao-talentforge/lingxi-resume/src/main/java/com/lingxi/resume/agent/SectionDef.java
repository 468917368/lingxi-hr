package com.lingxi.resume.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent → Tool 的章节定义（分段指令）
 *
 * <p>由 Agent（Mock/真实 LLM）在语义理解后产出：识别出简历文本中的信息章节边界，
 * 将每个章节的标题与所属正文行结构化后传给 parse_resume 工具；
 * 工具侧不再做标题识别，只按此定义组装 card_structure。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Data
public class SectionDef {

    /** 章节标题（Agent 识别，如"教育经历"/"Work Experience"） */
    private String title;

    /** 该章节包含的所有正文行（Agent 已判定归属） */
    private List<String> lines;
}

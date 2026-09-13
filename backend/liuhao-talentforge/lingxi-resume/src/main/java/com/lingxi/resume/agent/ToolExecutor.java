package com.lingxi.resume.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.resume.exception.ResumeErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具分发执行器
 *
 * <p>收到模型（Mock/真实 LLM）的工具调用请求后本地执行：
 * <ul>
 *   <li>{@code parse_resume} —— 调用 {@link RuleParseFallback} 按规则解析简历文本</li>
 * </ul>
 * 结果以 JSON 返回并回填为 tool 消息，供模型产出 final 输出。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    /** parse_resume 工具名 */
    public static final String TOOL_PARSE_RESUME = "parse_resume";

    private final RuleParseFallback ruleParseFallback;
    private final ObjectMapper objectMapper;

    /**
     * 执行工具调用
     *
     * @param toolName 工具名
     * @param argsJson 参数 JSON（parse_resume 可携带 Agent 识别的 sections 章节边界）
     * @param context  工具执行上下文
     * @return 执行结果 JSON（card_structure / ability_model / resume_md 同 final 结构）
     */
    public String execute(String toolName, String argsJson, ToolContext context) {
        switch (toolName) {
            case TOOL_PARSE_RESUME:
                return doParseResume(context, argsJson);
            default:
                log.warn("未知工具调用: {}", toolName);
                throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                        "Agent 调用了未知工具: " + toolName);
        }
    }

    /**
     * 解析 Agent 传入的 sections 参数（JSON 数组 → SectionDef 列表）
     *
     * @return 章节定义列表；参数缺失/格式异常/空数组返回 null（走规则兜底）
     */
    private List<SectionDef> parseSections(String argsJson) {
        if (argsJson == null || argsJson.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(argsJson);
            JsonNode sectionsNode = root.path("sections");
            if (sectionsNode.isMissingNode() || !sectionsNode.isArray() || sectionsNode.size() == 0) {
                return null;
            }
            List<SectionDef> sections = new ArrayList<>();
            for (JsonNode node : sectionsNode) {
                SectionDef def = new SectionDef();
                def.setTitle(node.path("title").asText());
                List<String> lines = new ArrayList<>();
                JsonNode linesNode = node.path("lines");
                if (linesNode.isArray()) {
                    for (JsonNode lineNode : linesNode) {
                        lines.add(lineNode.asText());
                    }
                }
                def.setLines(lines);
                sections.add(def);
            }
            return sections;
        } catch (Exception e) {
            log.warn("parse_resume: sections 参数解析失败，走规则兜底: {}", argsJson, e);
            return null;
        }
    }

    /**
     * parse_resume：优先按 Agent 识别的章节边界纯装配；Agent 未提供时规则引擎兜底分段
     */
    private String doParseResume(ToolContext context, String argsJson) {
        String text = context.getTikaText();
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历文本为空，无法解析");
        }
        List<SectionDef> sections = parseSections(argsJson);
        AgentResult result;
        if (sections != null && !sections.isEmpty()) {
            // Agent 已语义识别章节边界 → 纯装配
            log.info("parse_resume: 使用 Agent 识别的 {} 个章节边界装配", sections.size());
            result = ruleParseFallback.buildFromSections(sections, text);
        } else {
            // Agent 未提供章节信息 → 规则引擎兜底分段
            log.warn("parse_resume: Agent 未提供 sections，回退到规则引擎分段");
            result = ruleParseFallback.parse(text);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("card_structure", result.getCardStructure());
        Map<String, Object> abilityModel = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("professional_skill", result.getProfessionalSkillScore());
        scores.put("work_experience", result.getWorkExperienceScore());
        scores.put("industry_knowledge", result.getIndustryKnowledgeScore());
        scores.put("comprehensive_quality", result.getComprehensiveQualityScore());
        scores.put("learning_growth", result.getLearningGrowthScore());
        abilityModel.put("scores", scores);
        abilityModel.put("sub_dimensions", result.getSubDimensions());
        output.put("ability_model", abilityModel);
        output.put("resume_md", result.getResumeMd());
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "工具结果序列化失败", e);
        }
    }
}

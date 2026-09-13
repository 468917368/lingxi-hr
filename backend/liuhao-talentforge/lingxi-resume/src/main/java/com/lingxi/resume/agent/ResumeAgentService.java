package com.lingxi.resume.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.resume.exception.ResumeErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resume Agent 事件处理服务
 *
 * <p>接收 PDF 文件 URL，经 {@link com.lingxi.resume.agent.impl.RealBaibaoxiangClient} 内部
 * Tika 提取文本后（见 {@code RealBaibaoxiangClient.chatA2A()} →
 * {@code extractPdfText()}），作为 query 发送百宝箱智能体。
 *
 * <p>向百宝箱发起流式对话，接收并转发 A2A 事件、解析最终产物。
 * Agent 循环由百宝箱工作流引擎管控（C 不可见），C 只做事件转发 + 产物解析。
 *
 * <p>LLM 一次返回结构化 JSON：{personal, card_structure, ability_model, resume_md}
 * —— 个人信息由 LLM 语义提取（优于正则），直接落 resume 表独立列。
 *
 * <p>降级路径：A2A 未产出 artifact 时回退到 {@link RuleParseFallback} 本地规则引擎。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAgentService {

    private final BaibaoxiangClient baibaoxiangClient;
    private final RuleParseFallback ruleParseFallback;
    private final ObjectMapper objectMapper;

    // 注：解析 prompt 在 RealBaibaoxiangClient.buildQuery() 中按模式动态构建（CARD/SCORE/DIAGNOSIS/null 全量）；
    // 诊断 System Prompt 在百宝箱平台诊断工作流节点配置，Java 侧不维护。

    /**
     * 发起对话（PDF 经 Tika 提取文本后作为 query 发送）→ 转发事件 → 解析产物
     *
     * <p>文本提取在 {@link com.lingxi.resume.agent.impl.RealBaibaoxiangClient#chatA2A}
     * 内部完成：MinIO 下载 → Tika 提取 → 拼 prompt → POST 百宝箱。
     *
     * @param pdfFileUrl 简历 PDF 的可访问 URL（MinIO presigned URL）
     * @param resumeId   简历 ID（用作 contextId）
     * @param listener   Agent 事件监听（SSE 推送），可为 null
     * @param cancelled  取消标记（客户端断开）
     * @return 解析产物（含 personal 个人信息 + card_structure + ability_model + resume_md）
     */
    public AgentResult run(String pdfFileUrl, Long resumeId,
                           AgentEventListener listener, AtomicBoolean cancelled) {
        return run(pdfFileUrl, null, resumeId, listener, cancelled);
    }

    /**
     * 按模式发起 A2A 对话（阶段化解析）
     *
     * @param pdfFileUrl 简历 PDF 可访问 URL
     * @param mode       解析模式：BaibaoxiangClient.MODE_CARD（仅卡片）/ MODE_SCORE（仅评分），null=全量
     * @param resumeId   简历ID
     * @param listener   SSE 事件监听（可为 null）
     * @param cancelled  取消标记
     */
    public AgentResult run(String pdfFileUrl, String mode, Long resumeId,
                           AgentEventListener listener, AtomicBoolean cancelled) {

        // ① 发起对话（PDF 经 Tika 提取文本后发送百宝箱）
        Iterable<A2AEvent> events = baibaoxiangClient.chatA2A(
                pdfFileUrl, mode, resumeId.toString(), cancelled);

        String finalOutput = null;

        // ② 逐事件转发
        for (A2AEvent event : events) {
            if (cancelled.get()) {
                throw new AgentCancelledException("A2A 对话被取消");
            }

            if (event instanceof A2AEvent.TextEvent) {
                // LLM thinking 文本 → SSE:thinking
                String content = ((A2AEvent.TextEvent) event).getContent();
                notify(listener, "thinking", singletonMap("content", content));

            } else if (event instanceof A2AEvent.ProgressEvent) {
                // card_structure 章节级进度 → SSE:progress（前端逐章渲染卡片骨架）
                A2AEvent.ProgressEvent progress = (A2AEvent.ProgressEvent) event;
                notify(listener, "progress", progressMap(progress));

            } else if (event instanceof A2AEvent.ArtifactEvent) {
                // 智能体产物 → SSE:final（最后一块时）
                A2AEvent.ArtifactEvent artifact = (A2AEvent.ArtifactEvent) event;
                if (artifact.isLastChunk()) {
                    finalOutput = artifact.getContent();
                }

            } else if (event instanceof A2AEvent.StatusEvent) {
                // 状态更新
                A2AEvent.StatusEvent status = (A2AEvent.StatusEvent) event;
                if ("failed".equals(status.getState())) {
                    notify(listener, "error",
                            errorMap(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "智能体执行失败"));
                    throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "智能体执行失败");
                }
            }
        }

        // ③ 解析 final → AgentResult
        if (finalOutput != null) {
            log.info("A2A 对话完成: resumeId={}", resumeId);
            return parseOutput(finalOutput);
        }

        // 降级兜底：A2A 未产出 artifact，本地规则引擎执行（无个人信息——不编造）。
        // 2026-08-10：降级改用本简历的 Tika 原文（按 contextId 取，并发解析互不覆盖）——
        // 之前传固定错误文本，规则引擎会产出垃圾卡片且仍置 COMPLETED；null/空时回退固定文案
        log.warn("A2A 未产出 artifact，降级到本地规则引擎: resumeId={}", resumeId);
        String fallbackText = baibaoxiangClient.getLastResumeText(resumeId.toString());
        return ruleParseFallback.parse(
                fallbackText != null && !fallbackText.isEmpty()
                        ? fallbackText : "简历解析失败，无法读取内容");
    }

    /**
     * Agent 事件监听接口（SSE 推送回调）
     */
    @FunctionalInterface
    public interface AgentEventListener {
        void onEvent(String eventName, Object data);
    }

    // ==================== 私有方法 ====================

    /**
     * 解析 LLM 产出 JSON 为 AgentResult
     *
     * <p>结构：{personal:{name,phone,email,wechat},
     *          card_structure, ability_model:{scores,sub_dimensions}, resume_md}
     */
    private AgentResult parseOutput(String json) {
        return parseOutputInternal(json);
    }

    /**
     * 三层兜底解析：清洗 → 标准解析 → 逐个字段提取
     */
    private AgentResult parseOutputInternal(String rawJson) {
        // === 第1层：标准清洗 ===
        String json = cleanLlmOutput(rawJson);

        // === 第2层：标准解析 + 括号修复重试 ===
        JsonNode root = tryStandardParse(json);
        if (root != null) {
            return buildResult(root);
        }

        // === 第3层：逐个字段正则提取（一个字段坏了不影响其他） ===
        log.warn("标准 JSON 解析失败，启用逐字段提取");
        AgentResult result = new AgentResult();

        // card_structure（最重要，前端靠它渲染）
        try {
            String cardJson = extractField(json, "card_structure");
            if (cardJson != null) {
                result.setCardStructure(objectMapper.readTree(cardJson));
            }
        } catch (Exception e) {
            log.warn("card_structure 字段提取失败: {}", e.getMessage());
        }

        // personal
        try {
            String personalJson = extractField(json, "personal");
            if (personalJson != null) {
                JsonNode personal = objectMapper.readTree(personalJson);
                result.setName(textOrNull(personal.path("name")));
                result.setPhone(textOrNull(personal.path("phone")));
                result.setEmail(textOrNull(personal.path("email")));
                result.setWechat(textOrNull(personal.path("wechat")));
            }
        } catch (Exception e) {
            log.warn("personal 字段提取失败: {}", e.getMessage());
        }

        // ability_model
        try {
            String amJson = extractField(json, "ability_model");
            if (amJson != null) {
                JsonNode am = objectMapper.readTree(amJson);
                JsonNode scores = am.path("scores");
                result.setProfessionalSkillScore(scores.path("professional_skill").asInt(0));
                result.setWorkExperienceScore(scores.path("work_experience").asInt(0));
                result.setIndustryKnowledgeScore(scores.path("industry_knowledge").asInt(0));
                result.setComprehensiveQualityScore(scores.path("comprehensive_quality").asInt(0));
                result.setLearningGrowthScore(scores.path("learning_growth").asInt(0));
                result.setSubDimensions(am.path("sub_dimensions").toString());
            }
        } catch (Exception e) {
            log.warn("ability_model 字段提取失败: {}", e.getMessage());
        }

        // resume_md
        try {
            String md = extractField(json, "resume_md");
            if (md != null) {
                result.setResumeMd(cleanJsonString(md));
            }
        } catch (Exception e) {
            log.warn("resume_md 字段提取失败: {}", e.getMessage());
        }

        if (result.getCardStructure() == null) {
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                    "Agent 输出格式异常，无法提取 card_structure");
        }
        return result;
    }

    // ==================== JSON 工具方法 ====================

    /** 标准清洗 */
    private String cleanLlmOutput(String json) {
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*", "")
                       .replaceFirst("\\s*```$", "").trim();
        }
        int braceStart = json.indexOf('{');
        if (braceStart > 0) {
            json = json.substring(braceStart);
        }
        int braceEnd = json.lastIndexOf('}');
        if (braceEnd > 0 && braceEnd < json.length() - 1) {
            json = json.substring(0, braceEnd + 1);
        }
        return json;
    }

    /** 标准解析 + 括号修复重试 */
    private JsonNode tryStandardParse(String json) {
        // 尝试1：直接解析
        try {
            return objectMapper.readTree(json);
        } catch (Exception e1) {
            log.debug("JSON 首次解析失败: {}", e1.getMessage());
        }
        // 尝试2：补丢失的 ]
        try {
            String repaired = json;
            int lastBrace = repaired.lastIndexOf('}');
            if (lastBrace > 0) {
                repaired = repaired.substring(0, lastBrace + 1) + "]"
                        + repaired.substring(lastBrace + 1);
            }
            return objectMapper.readTree(repaired);
        } catch (Exception e2) {
            log.debug("JSON 补]后仍失败: {}", e2.getMessage());
        }
        // 尝试3：用花括号计数补全
        try {
            String repaired = fixBrackets(json);
            return objectMapper.readTree(repaired);
        } catch (Exception e3) {
            log.debug("JSON 计数补全后仍失败: {}", e3.getMessage());
        }
        // 尝试4：修复缺逗号（LLM 输出长 JSON 常丢逗号，如 "主修课程：" 后直接跟内容）
        try {
            String repaired = fixMissingCommas(json);
            return objectMapper.readTree(repaired);
        } catch (Exception e4) {
            log.debug("JSON 补逗号后仍失败: {}", e4.getMessage());
        }
        return null;
    }

    /**
     * 修复缺逗号：在对象/数组闭合符（} 或 ]）与下一个键名（"）之间补逗号。
     * <p>跳过字符串内容（含转义），仅在 JSON 结构层修复——这些组合在合法 JSON 中必然非法，
     * 误修风险低。如：{"a":1 "b":2} → {"a":1, "b":2}
     */
    private String fixMissingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        char prev = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            // 字符串内：原样保留（含转义对）
            if (c == '\\' && inString) {
                sb.append(c);
                if (i + 1 < json.length()) {
                    sb.append(json.charAt(++i));
                }
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                prev = c;
                continue;
            }
            if (!inString && (prev == '}' || prev == ']') && c == '"') {
                // 闭合符后直接是键名引号 → 缺逗号
                sb.append(',');
            }
            sb.append(c);
            prev = c;
        }
        return sb.toString();
    }

    /**
     * 花括号/方括号计数补全。
     * <p>必须跳过字符串内容（简历文本可能含 []{} 字符），否则计数错乱导致补全位置错误。
     * 仅补缺失的闭合符（LLM 常丢结尾的 ]/}），不补开放符。
     */
    private String fixBrackets(String json) {
        int braceCount = 0, bracketCount = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) {
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
        }
        StringBuilder sb = new StringBuilder(json);
        while (bracketCount > 0) { sb.append(']'); bracketCount--; }
        while (braceCount > 0) { sb.append('}'); braceCount--; }
        return sb.toString();
    }

    /** 正则提取 JSON 字段值 */
    private String extractField(String json, String fieldName) {
        // 匹配 "fieldName": { ... }  或  "fieldName": "..."
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"" + fieldName + "\"\\s*:\\s*");
        java.util.regex.Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        int start = m.end();
        if (start >= json.length()) return null;

        char firstChar = json.charAt(start);
        if (firstChar == '{') {
            return extractBraced(json, start);
        } else if (firstChar == '\"') {
            // 字符串值：找到闭合引号
            int end = json.indexOf('"', start + 1);
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf('"', end + 1);
            }
            return end > 0 ? json.substring(start + 1, end) : null;
        } else if (firstChar == '[') {
            return extractBracketed(json, start);
        }
        return null;
    }

    /** 从 start 位置提取 { ... } 子串（处理嵌套花括号） */
    private String extractBraced(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /** 从 start 位置提取 [ ... ] 子串 */
    private String extractBracketed(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** 清理 JSON 字符串值中的转义问题 */
    private String cleanJsonString(String s) {
        if (s == null) return "";
        // 去首尾引号
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private AgentResult buildResult(JsonNode root) {
        AgentResult result = new AgentResult();
        JsonNode personal = root.path("personal");
        result.setName(textOrNull(personal.path("name")));
        result.setPhone(textOrNull(personal.path("phone")));
        result.setEmail(textOrNull(personal.path("email")));
        result.setWechat(textOrNull(personal.path("wechat")));
        result.setCardStructure(root.path("card_structure"));
        JsonNode scores = root.path("ability_model").path("scores");
        result.setProfessionalSkillScore(scores.path("professional_skill").asInt(0));
        result.setWorkExperienceScore(scores.path("work_experience").asInt(0));
        result.setIndustryKnowledgeScore(scores.path("industry_knowledge").asInt(0));
        result.setComprehensiveQualityScore(scores.path("comprehensive_quality").asInt(0));
        result.setLearningGrowthScore(scores.path("learning_growth").asInt(0));
        result.setSubDimensions(root.path("ability_model").path("sub_dimensions").toString());
        result.setResumeMd(root.path("resume_md").asText(""));
        return result;
    }

    /** JsonNode → String，null/缺失返回 null */
    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private void notify(AgentEventListener listener, String event, Object data) {
        if (listener != null) {
            try {
                listener.onEvent(event, data);
            } catch (Exception e) {
                log.debug("Agent 事件推送失败: event={}", event, e);
            }
        }
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private Map<String, Object> progressMap(A2AEvent.ProgressEvent progress) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stage", progress.getStage());
        map.put("sections_done", progress.getSectionsDone());
        map.put("titles", progress.getTitles());
        return map;
    }

    private Map<String, Object> errorMap(int code, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message);
        return map;
    }
}

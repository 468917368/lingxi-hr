package com.lingxi.job.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Mock 百宝箱智能体客户端（默认实现，可测）
 * <p>
 * {@code ai.agent.mock=true}（缺省默认）时生效，本地模拟内部事件流，不发起真实网络调用：
 * <ul>
 *   <li>{@link #parseJd}：返回固定画像草稿 JSON（与 JdParseResponse 结构一致）</li>
 *   <li>{@link #streamInterview}：按 {@link AgentStreamEvent} 协议回调
 *   {@code HEADER → CONTENT×3（分段 result JSON）→ COMPLETE}</li>
 * </ul>
 * </p>
 * <p>
 * 真实百宝箱接入后由 {@code RealBaibaoxiangAgentClient}（mock=false）替换。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.agent.mock", havingValue = "true", matchIfMissing = true)
public class MockBaibaoxiangAgentClient implements BaibaoxiangAgentClient {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;

    @Override
    public String parseJd(String prompt, String userId) {
        // 模拟 JD 解析：返回画像草稿 JSON（结构对齐 JdParseResponse）
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("jobType", "JAVA_BACKEND");

            List<Map<String, Object>> coreSkills = new ArrayList<>();
            coreSkills.add(skill("Java", "3", true, 0.9));
            coreSkills.add(skill("Spring Boot", "3", true, 0.85));
            root.put("coreSkills", coreSkills);

            List<Map<String, Object>> softSkills = new ArrayList<>();
            softSkills.add(skill("沟通能力", "3", true, 0.8));
            root.put("softSkills", softSkills);

            root.put("minExperienceYears", 3);
            root.put("educationRequirement", "BACHELOR");

            Map<String, Object> salary = new LinkedHashMap<>();
            salary.put("currency", "CNY");
            salary.put("period", "MONTH");
            salary.put("minAmount", 2000000);
            salary.put("maxAmount", 3000000);
            root.put("salary", salary);

            List<Map<String, Object>> interviewFocus = new ArrayList<>();
            interviewFocus.add(skill("Spring 原理", "3", true, 0.8));
            root.put("interviewFocus", interviewFocus);

            root.put("jdSummary", "负责核心后端服务设计与开发，要求 3 年以上 Java 经验。");

            List<String> warnings = new ArrayList<>();
            warnings.add("薪资已识别");
            root.put("warnings", warnings);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Mock JD 解析结果序列化失败", e);
            throw new IllegalStateException("Mock 画像序列化失败", e);
        }
    }

    @Override
    public void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                                Consumer<AgentStreamEvent> consumer) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        consumer.accept(AgentStreamEvent.header(requestId));
        if (isCancelled(cancelled)) {
            return;
        }
        // 【本次出题控制】requiredTypes：按 requiredTypes 生成对应题型各 1 题；纯 AI（4 标准题型）亦然
        List<String> requiredTypes = extractRequiredTypes(prompt);
        String resultJson = resultJson(requestId, requiredTypes);
        // 分段 CONTENT 模拟流式增量输出（累积后为完整 result JSON）
        int third = resultJson.length() / 3;
        consumer.accept(AgentStreamEvent.content(resultJson.substring(0, third)));
        if (isCancelled(cancelled)) {
            return;
        }
        consumer.accept(AgentStreamEvent.content(resultJson.substring(third, third * 2)));
        if (isCancelled(cancelled)) {
            return;
        }
        consumer.accept(AgentStreamEvent.content(resultJson.substring(third * 2)));
        consumer.accept(AgentStreamEvent.complete(requestId));
    }

    /** 取消判断（null 安全） */
    private boolean isCancelled(AtomicBoolean cancelled) {
        return cancelled != null && cancelled.get();
    }

    /** 构造技能子项 */
    private Map<String, Object> skill(String name, String level, boolean required, double confidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("level", level);
        m.put("required", required);
        m.put("confidence", confidence);
        return m;
    }

    /**
     * 提取 prompt 中【本次出题控制】块的「requiredTypes：<逗号分隔>」行 → 题型列表；
     * 无则兼容旧「缺失题型：」行。空列表 → resultJson 生成 4 个标准题型固定结果。
     */
    private List<String> extractRequiredTypes(String prompt) {
        List<String> result = parseMarkerLine(prompt, "requiredTypes：");
        if (result.isEmpty()) {
            result = parseMarkerLine(prompt, "缺失题型："); // 兼容旧格式
        }
        return result;
    }

    /** 解析 prompt 中含指定标记的行（逗号分隔题型，trim 去空） */
    private List<String> parseMarkerLine(String prompt, String marker) {
        List<String> result = new ArrayList<>();
        if (prompt == null) {
            return result;
        }
        for (String line : prompt.split("\\R")) {
            int idx = line.indexOf(marker);
            if (idx >= 0) {
                String types = line.substring(idx + marker.length());
                for (String t : types.split(",")) {
                    String type = t.trim();
                    if (!type.isEmpty()) {
                        result.add(type);
                    }
                }
                break;
            }
        }
        return result;
    }

    /**
     * result 事件 JSON：missingTypes 非空 → 只生成对应题型各 1 题（补题场景）；
     * 空 → 4 个标准题型固定结果（退化路径）
     */
    private String resultJson(String requestId, List<String> missingTypes) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("requestId", requestId);
            root.put("generationMode", "AI_GENERATED");
            root.put("isPersonalized", true);
            root.put("dataCompleteness", "SUFFICIENT");

            List<Map<String, Object>> questions = new ArrayList<>();
            if (missingTypes == null || missingTypes.isEmpty()) {
                // 全量分支：4 个标准题型固定真实题（题目数据集中于 mockQuestionForType）
                for (String t : new String[]{"BASIC", "PROJECT", "BOUNDARY", "COMPREHENSIVE"}) {
                    questions.add(mockQuestionForType(t));
                }
            } else {
                // 补题分支：按缺失题型返回真实模拟题（不复用提示词模板）
                for (String type : missingTypes) {
                    questions.add(mockQuestionForType(type));
                }
            }
            root.put("questions", questions);

            root.put("timestamp", LocalDateTime.now().format(TS));
            return objectMapper.writeValueAsString(root);
        } catch (IllegalArgumentException e) {
            // 未知题型 → fail-fast（编程错误，向上暴露，不静默产生垃圾内容）
            throw e;
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 按题型取真实模拟题（题目数据集中于此，全量/补题分支共用；补题不新增题库；
     * 未知题型 fail-fast 抛异常，避免静默产生提示词垃圾）
     */
    private Map<String, Object> mockQuestionForType(String type) {
        switch (type) {
            case "BASIC":
                return question("BASIC", "MEDIUM", "请解释 Java 内存模型及 volatile 的作用。",
                        "考察并发基础", "JMM 三大特性、happens-before、volatile 可见性与有序性");
            case "PROJECT":
                return question("PROJECT", "MEDIUM", "请描述你主导过的一个高并发服务的设计与压测结果。",
                        "考察项目深挖", "技术选型、瓶颈分析、容量评估");
            case "BOUNDARY":
                return question("BOUNDARY", "HARD", "Spring 事务传播机制中 REQUIRES_NEW 在什么场景下会导致问题？",
                        "考察能力边界", "嵌套事务、连接复用、回滚语义");
            case "COMPREHENSIVE":
                return question("COMPREHENSIVE", "MEDIUM", "如果线上出现接口超时，你会如何排查？",
                        "考察综合素养", "链路追踪、慢查询、线程池、缓存一致性");
            default:
                throw new IllegalArgumentException("未知题型: " + type);
        }
    }

    /** 单道题 */
    private Map<String, Object> question(String type, String difficulty, String content,
                                         String keyPoints, String referenceAnswer) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("type", type);
        q.put("difficulty", difficulty);
        q.put("content", content);
        q.put("keyPoints", keyPoints);
        q.put("referenceAnswer", referenceAnswer);
        List<Map<String, Object>> dims = new ArrayList<>();
        dims.add(dim("技术深度", 0.6));
        dims.add(dim("表达能力", 0.2));
        dims.add(dim("经验匹配", 0.2));
        q.put("evaluationDimensions", dims);
        q.put("sourceType", "AI_GENERATED");
        return q;
    }

    private Map<String, Object> dim(String name, double weight) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("weight", weight);
        return m;
    }
}

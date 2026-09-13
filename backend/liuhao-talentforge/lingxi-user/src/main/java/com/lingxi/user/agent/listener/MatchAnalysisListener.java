package com.lingxi.user.agent.listener;

import cn.tbox.sdk.TboxClient;
import cn.tbox.sdk.model.request.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.constant.RocketMQConstant;
import com.lingxi.common.domain.ApplicationEvent;
import com.lingxi.common.domain.Result;
import com.lingxi.user.feign.InternalApplicationFeignClient;
import com.lingxi.user.feign.JobFeignClient;
import com.lingxi.user.feign.ResumeFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 匹配度 AI 分析监听器（RocketMQ 消费者）
 * <p>
 * 触发时机：用户投递岗位时，lingxi-resume 发送 NEW_APPLICATION 事件到 RocketMQ。
 * </p>
 * <p>
 * 处理流程：
 * ① 收到投递事件（RocketMQ 消费）
 * ② 异步获取简历 + 岗位信息（Feign 调用）
 * ③ 调百宝箱 LLM 分析匹配度（生成 score/advantages/risks/suggestions）
 * ④ 分析结果写回 resume_application 表（Feign 调用）
 * </p>
 * <p>
 * 降级策略：
 * - LLM 调用失败/返回无效 → 使用规则引擎降级分析（buildFallbackAnalysis）
 * - 简历/岗位获取失败 → 跳过分析（不影响投递主流程）
 * </p>
 * <p>
 * LLM 输出格式（JSON）：
 * {
 *   "score": 85,           // 匹配分数 0-100
 *   "advantages": [...],   // 优势列表
 *   "risks": [...],        // 风险列表
 *   "suggestions": [...],  // 建议列表
 *   "summary": "..."       // 一句话总结
 * }
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.TOPIC_APPLICATION_EVENT,       // 监听的主题
        selectorExpression = RocketMQConstant.TAG_NEW_APPLICATION,  // 只消费 NEW_APPLICATION 标签
        consumerGroup = "match-analysis-consumer"               // 消费者组（同一组内只消费一次）
)
public class MatchAnalysisListener implements RocketMQListener<ApplicationEvent> {

    /** 简历服务 Feign（获取用户简历） */
    private final ResumeFeignClient resumeFeignClient;
    /** 岗位服务 Feign（获取岗位详情） */
    private final JobFeignClient jobFeignClient;
    /** 投递服务 Feign（写回 AI 分析结果） */
    private final InternalApplicationFeignClient applicationFeignClient;
    /** 百宝箱 SDK 客户端（调 LLM 分析） */
    private final TboxClient tboxClient;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /** 百宝箱分析应用ID（配置文件中 baibaoxiang.analysis.app-id） */
    @Value("${baibaoxiang.analysis.app-id:}")
    private String analysisAppId;

    /**
     * RocketMQ 消费入口（同步，只做日志 + 转异步）
     * <p>
     * 注意：不能在这里做耗时操作，否则会阻塞 MQ 消费线程。
     * 实际处理在 processAsync() 中异步执行。
     * </p>
     */
    @Override
    public void onMessage(ApplicationEvent event) {
        log.info("收到投递事件: applicationId={}, jobId={}, candidateId={}",
                event.getApplicationId(), event.getJobId(), event.getCandidateId());

        // 异步处理，不阻塞 MQ 消费线程
        processAsync(event);
    }

    /**
     * 异步执行 AI 分析（独立线程池，不阻塞 MQ 消费）
     * <p>
     * 流程：获取简历 → 获取岗位 → LLM 分析 → 写回数据库
     * 任何一步失败都不影响投递主流程（只记日志）
     * </p>
     *
     * @param event 投递事件（含 applicationId、jobId、candidateId）
     */
    @Async("asyncExecutor")
    public void processAsync(ApplicationEvent event) {
        try {
            // 1. 获取简历信息
            Map<String, Object> resumeData = getResumeData(event.getCandidateId());
            if (resumeData == null || resumeData.isEmpty()) {
                log.warn("获取简历为空，跳过AI分析: candidateId={}", event.getCandidateId());
                return;
            }

            // 2. 获取岗位信息
            Map<String, Object> jobData = getJobData(event.getJobId());
            if (jobData == null || jobData.isEmpty()) {
                log.warn("获取岗位为空，跳过AI分析: jobId={}", event.getJobId());
                return;
            }

            // 3. 调百宝箱 LLM 分析
            Map<String, Object> analysis = callLlmAnalysis(resumeData, jobData);
            log.info("LLM分析结果: applicationId={}, isNull={}, keys={}",
                    event.getApplicationId(), analysis == null,
                    analysis != null ? analysis.keySet() : "null");

            if (analysis == null || analysis.isEmpty() || !analysis.containsKey("score")) {
                log.warn("AI分析结果无效，使用降级分析: applicationId={}", event.getApplicationId());
                analysis = buildFallbackAnalysis(resumeData, jobData);
            }

            log.info("最终分析结果: applicationId={}, score={}, keys={}",
                    event.getApplicationId(), analysis.get("score"), analysis.keySet());

            // 4. 写回数据库
            updateAiAnalysis(event.getApplicationId(), analysis);

            log.info("AI分析完成: applicationId={}", event.getApplicationId());
        } catch (Exception e) {
            log.error("AI分析失败: applicationId={}", event.getApplicationId(), e);
        }
    }

    /**
     * 获取简历数据（Feign 调用 lingxi-resume）
     *
     * @param userId 用户ID
     * @return 简历数据 Map（含 skills/education/workYears 等），失败返回 null
     */
    private Map<String, Object> getResumeData(Long userId) {
        try {
            Result<Map<String, Object>> result = resumeFeignClient.getUserResume(userId);
            return result != null ? result.getData() : null;
        } catch (Exception e) {
            log.warn("获取简历失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 获取岗位数据（Feign 调用 lingxi-job）
     *
     * @param jobId 岗位ID
     * @return 岗位数据 Map（含 title/educationRequirement/minExperienceYears 等），失败返回 null
     */
    private Map<String, Object> getJobData(Long jobId) {
        try {
            Result<Map<String, Object>> result = jobFeignClient.getJobDetail(jobId);
            return result != null ? result.getData() : null;
        } catch (Exception e) {
            log.warn("获取岗位失败: jobId={}", jobId, e);
            return null;
        }
    }

    /**
     * 调百宝箱 LLM 分析简历与岗位匹配度
     * <p>
     * 流程：
     * 1. 构建 Prompt（简历信息 + 岗位信息 + 输出格式要求）
     * 2. 调百宝箱 LLM（流式返回，遍历拼接）
     * 3. 解析 LLM 返回的 JSON（score/advantages/risks/suggestions/summary）
     * </p>
     * <p>
     * 降级：LLM 调用失败或返回无效 → 使用规则引擎降级分析（buildFallbackAnalysis）
     * </p>
     *
     * @param resumeData 简历数据
     * @param jobData    岗位数据
     * @return 分析结果 Map（含 score/advantages/risks/suggestions/summary）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlmAnalysis(Map<String, Object> resumeData, Map<String, Object> jobData) {
        try {
            // 1. 构建 Prompt
            String prompt = buildAnalysisPrompt(resumeData, jobData);

            // 2. 调百宝箱 LLM（流式返回，需要遍历拼接）
            ChatRequest request = new ChatRequest(analysisAppId, prompt, "system");
            Object result = tboxClient.chat(request);

            StringBuilder sb = new StringBuilder();
            if (result instanceof Iterable) {
                for (Object item : (Iterable<?>) result) {
                    if (item instanceof Map) {
                        Map<?, ?> chunk = (Map<?, ?>) item;
                        // 提取文本（兼容工作流事件格式）
                        String[] extracted = extractTextFromChunk(chunk);
                        String text = extracted[1];
                        if (text != null && !text.isEmpty()) {
                            sb.append(text);
                        }
                    }
                }
            } else if (result != null) {
                sb.append(result.toString());
            }

            String llmResponse = sb.toString().trim();
            log.info("百宝箱AI分析返回(长度={}): {}", llmResponse.length(),
                    llmResponse.substring(0, Math.min(300, llmResponse.length())));

            // 3. 解析 LLM 返回的 JSON
            if (llmResponse.isEmpty()) {
                log.warn("百宝箱返回空内容，使用降级分析");
                return buildFallbackAnalysis(resumeData, jobData);
            }
            return parseLlmResponse(llmResponse);
        } catch (Exception e) {
            log.warn("调百宝箱分析失败，使用降级分析", e);
            return buildFallbackAnalysis(resumeData, jobData);
        }
    }

    /**
     * 构建分析 Prompt（发给百宝箱 LLM）
     * <p>
     * 包含：候选人信息（学历/年限/技能）+ 岗位信息（名称/学历要求/经验要求）
     * + 输出格式要求（JSON：score/advantages/risks/suggestions/summary）
     * </p>
     */
    private String buildAnalysisPrompt(Map<String, Object> resumeData, Map<String, Object> jobData) {
        String userSkills = String.join("、", extractList(resumeData, "skills"));
        String userEducation = (String) resumeData.getOrDefault("education", "未知");
        String userYears = (String) resumeData.getOrDefault("workYears", "0");

        String jobTitle = (String) jobData.getOrDefault("title", "未知岗位");
        String jobEducation = (String) jobData.getOrDefault("educationRequirement", "无要求");
        Integer jobYears = jobData.get("minExperienceYears") != null ?
                ((Number) jobData.get("minExperienceYears")).intValue() : 0;

        return "你是一个专业的HR助手，请分析以下简历与岗位的匹配度。\n\n" +
                "## 候选人信息\n" +
                "- 学历：" + userEducation + "\n" +
                "- 工作年限：" + userYears + "\n" +
                "- 技能：" + (userSkills.isEmpty() ? "未填写" : userSkills) + "\n\n" +
                "## 岗位信息\n" +
                "- 岗位名称：" + jobTitle + "\n" +
                "- 学历要求：" + jobEducation + "\n" +
                "- 经验要求：" + jobYears + "年以上\n\n" +
                "## 输出要求\n" +
                "请严格以JSON格式输出分析结果，不要输出其他内容：\n" +
                "{\n" +
                "  \"score\": 85,\n" +
                "  \"advantages\": [\"优势1\", \"优势2\"],\n" +
                "  \"risks\": [\"风险1\", \"风险2\"],\n" +
                "  \"suggestions\": [\"建议1\", \"建议2\"],\n" +
                "  \"summary\": \"一句话总结\"\n" +
                "}\n\n" +
                "注意：score为0-100的整数，advantages/risks/suggestions各2-3条。";
    }

    /**
     * 从百宝箱 chunk 中提取文本（兼容工作流事件格式）
     *
     * @return [lane, text]
     */
    private String[] extractTextFromChunk(Map<?, ?> chunk) {
        String lane = null;
        String text = null;

        // 提取 lane 字段
        Object laneObj = chunk.get("lane");
        if (laneObj instanceof String) {
            lane = (String) laneObj;
        }

        // 提取文本（按优先级）
        for (String key : new String[]{"payload", "chunk", "text", "content"}) {
            Object val = chunk.get(key);
            if (val instanceof String && !((String) val).isEmpty()) {
                text = (String) val;
                break;
            }
        }

        // 递归查找嵌套
        if (text == null) {
            for (Object value : chunk.values()) {
                if (value instanceof Map) {
                    String[] nested = extractTextFromChunk((Map<?, ?>) value);
                    if (nested[1] != null) {
                        if (lane == null) lane = nested[0];
                        text = nested[1];
                        break;
                    }
                }
            }
        }

        return new String[]{lane, text};
    }

    /**
     * 解析 LLM 返回的 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLlmResponse(String llmResponse) {
        try {
            // 提取 JSON 部分
            int start = llmResponse.indexOf("{");
            int end = llmResponse.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                String json = llmResponse.substring(start, end);
                return objectMapper.readValue(json, Map.class);
            }
        } catch (Exception e) {
            log.warn("解析LLM返回JSON失败", e);
        }
        return null;
    }

    /**
     * 降级分析（LLM 调用失败时使用规则引擎）
     * <p>
     * 规则：
     * - 优势：有技能 / 年限满足要求
     * - 风险：年限不足 / 学历低于要求
     * - 建议：固定建议
     * - 分数：固定 70 分
     * </p>
     *
     * @param resumeData 简历数据
     * @param jobData    岗位数据
     * @return 降级分析结果 Map
     */
    private Map<String, Object> buildFallbackAnalysis(Map<String, Object> resumeData, Map<String, Object> jobData) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        List<String> advantages = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        String userEducation = (String) resumeData.getOrDefault("education", "");
        Integer userYears = parseWorkYears((String) resumeData.getOrDefault("workYears", "0"));
        List<String> userSkills = extractList(resumeData, "skills");
        String jobEducation = (String) jobData.getOrDefault("educationRequirement", "");
        Integer jobYears = jobData.get("minExperienceYears") != null ?
                ((Number) jobData.get("minExperienceYears")).intValue() : 0;
        String jobTitle = (String) jobData.getOrDefault("title", "");

        if (!userSkills.isEmpty()) {
            advantages.add("掌握" + String.join("、", userSkills.subList(0, Math.min(3, userSkills.size()))) + "等技能");
        }
        if (userYears >= jobYears) {
            advantages.add("工作年限" + userYears + "年，满足岗位要求");
        }
        if (userYears < jobYears) {
            risks.add("工作年限不足，岗位要求" + jobYears + "年");
        }
        if (isEducationLower(userEducation, jobEducation)) {
            risks.add("学历低于岗位要求");
        }
        suggestions.add("建议在面试中重点考察实际项目经验");

        analysis.put("score", 70);
        analysis.put("advantages", advantages);
        analysis.put("risks", risks);
        analysis.put("suggestions", suggestions);
        analysis.put("summary", "AI分析服务暂时不可用，已生成基础分析");

        return analysis;
    }

    /**
     * 更新 AI 分析结果到数据库（Feign 调用 lingxi-resume 内部接口）
     * <p>
     * 写入字段：
     * - ai_score: 匹配分数（0-100）
     * - ai_analysis: 完整分析结果 JSON（含 advantages/risks/suggestions/summary）
     * </p>
     *
     * @param applicationId 投递记录ID
     * @param analysis      分析结果 Map
     */
    private void updateAiAnalysis(Long applicationId, Map<String, Object> analysis) {
        try {
            // 提取分数（兼容多种字段名）
            Object score = analysis.get("score");
            if (score == null) score = analysis.get("matchScore");
            if (score == null) score = analysis.get("match_score");

            Map<String, Object> body = new HashMap<>();
            body.put("aiScore", score);
            body.put("aiAnalysis", analysis);

            log.info("更新AI分析: applicationId={}, aiScore={}, keys={}", applicationId, score, analysis.keySet());

            // 调用 lingxi-resume 的内部接口
            Result<Void> result = applicationFeignClient.updateAiAnalysis(applicationId, body);
            if (result != null && result.getCode() == 200) {
                log.info("AI分析结果已更新: applicationId={}", applicationId);
            } else {
                log.warn("AI分析结果更新失败: applicationId={}, result={}", applicationId, result);
            }
        } catch (Exception e) {
            log.warn("更新AI分析失败: applicationId={}", applicationId, e);
        }
    }

    /**
     * 解析工作年限字符串 → 数字
     * <p>
     * 支持格式："3年"、"3-5年"、"FRESH"、"应届" → 0
     * </p>
     */
    private Integer parseWorkYears(String workYears) {
        if (workYears == null || workYears.isEmpty()) return 0;
        if ("FRESH".equalsIgnoreCase(workYears) || "应届".equals(workYears)) return 0;
        try {
            String num = workYears.replaceAll("[^0-9]", "");
            if (num.isEmpty()) return 0;
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从 Map 中提取 List&lt;String&gt;（安全转换，类型不匹配返回空列表）
     */
    @SuppressWarnings("unchecked")
    private List<String> extractList(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) obj) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * 判断用户学历是否低于岗位要求
     * <p>
     * 学历等级：高中=1、大专=2、本科=3、硕士=4、博士=5
     * 支持中文和英文编码（BACHELOR/MASTER/PHD）
     * </p>
     */
    private boolean isEducationLower(String userEdu, String jobEdu) {
        Map<String, Integer> level = new HashMap<>();
        level.put("高中", 1); level.put("大专", 2); level.put("本科", 3);
        level.put("硕士", 4); level.put("博士", 5);
        level.put("BACHELOR", 3); level.put("MASTER", 4); level.put("PHD", 5);

        int userLevel = level.getOrDefault(userEdu, 0);
        int jobLevel = level.getOrDefault(jobEdu, 0);
        return jobLevel > 0 && userLevel < jobLevel;
    }
}

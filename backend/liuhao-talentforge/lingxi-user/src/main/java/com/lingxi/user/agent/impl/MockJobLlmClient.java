package com.lingxi.user.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.user.agent.JobAgentLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mock LLM 客户端（开发/演示模式）
 * <p>
 * 不调用真实 LLM API，根据工具返回的数据模拟生成响应。
 * 用于开发调试和演示，不依赖外部服务。
 * </p>
 * <p>
 * 支持的响应类型：
 * - 搜索结果格式化（generateSearchResponse）
 * - 推荐结果格式化（generateRecommendResponse）
 * - 匹配度分析格式化（generateMatchResponse）
 * - 岗位详情格式化（generateDetailResponse）
 * - 公司岗位格式化（generateCompanyJobsResponse）
 * - 意图分类模拟（generateClassifyResponse）
 * - 默认引导语（doGenerate）
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "job-agent.mock", havingValue = "true", matchIfMissing = true)
public class MockJobLlmClient implements JobAgentLlmClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generate(String appId, String query, String userId, long timeoutMs) {
        log.info("[MockJobLlm] appId={}, query={}", appId, truncate(query, 500));
        return doGenerate(query);
    }

    @Override
    public String generateStream(String appId, String query, String userId,
                                  long timeoutMs, ChunkCallback onChunk) {
        log.info("[MockJobLlm] 流式模式, appId={}, query={}", appId, truncate(query, 500));
        String fullText = doGenerate(query);

        if (onChunk != null) {
            int chunkSize = 20;
            for (int i = 0; i < fullText.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, fullText.length());
                String chunk = fullText.substring(i, end);
                onChunk.onChunk(chunk);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return fullText;
    }

    private String doGenerate(String query) {
        // 意图分类请求
        if (query.contains("意图分类助手")) {
            return generateClassifyResponse(query);
        }

        // 有工具返回结果
        if (query.contains("【工具调用结果】")) {
            return generateToolResponse(query);
        }

        // 有简历推荐数据
        if (query.contains("简历信息") && query.contains("搜索结果")) {
            return generateRecommendResponse(query);
        }

        // 默认引导语
        return "你好！我是小灵 👋\n\n" +
                "我可以帮你：\n" +
                "🔍 搜索岗位 — \"帮我找北京的Java岗位\"\n" +
                "🎯 智能推荐 — \"推荐适合我的岗位\"\n" +
                "📊 匹配分析 — \"我和这个岗位匹配度多少\"\n\n" +
                "请告诉我你想找什么样的工作？";
    }

    /**
     * Mock 意图分类响应
     */
    private String generateClassifyResponse(String query) {
        // 从 prompt 中提取用户消息
        String message = "";
        int idx = query.indexOf("用户消息：");
        if (idx > 0) {
            message = query.substring(idx + 5).trim();
        }

        String lower = message.toLowerCase();

        // 简单关键词匹配模拟 LLM 分类
        if (containsAny(lower, "推荐", "适合", "匹配")) {
            return "{\"intent\":\"recommend\",\"keywords\":[],\"city\":\"\",\"company\":\"\",\"jobType\":\"\"}";
        }
        if (containsAny(lower, "详情", "具体", "介绍", "要求")) {
            return "{\"intent\":\"detail\",\"keywords\":[],\"city\":\"\",\"company\":\"\",\"jobType\":\"\"}";
        }
        if (containsAny(lower, "匹配度", "够格")) {
            return "{\"intent\":\"match\",\"keywords\":[],\"city\":\"\",\"company\":\"\",\"jobType\":\"\"}";
        }
        if (containsAny(lower, "公司", "企业")) {
            return "{\"intent\":\"company\",\"keywords\":[],\"city\":\"\",\"company\":\"\",\"jobType\":\"\"}";
        }
        if (containsAny(lower, "找", "搜", "看", "岗位", "工作", "职位", "机会",
                "前端", "后端", "java", "python", "测试", "运维", "算法", "开发")) {
            // 提取关键词
            String keywords = "[]";
            String jobType = "";
            if (lower.contains("前端")) { keywords = "[\"前端\",\"frontend\",\"react\",\"vue\"]"; jobType = "FRONTEND"; }
            else if (lower.contains("java")) { keywords = "[\"java\",\"spring\",\"后端\"]"; jobType = "JAVA_BACKEND"; }
            else if (lower.contains("后端")) { keywords = "[\"后端\",\"backend\",\"java\",\"python\"]"; jobType = ""; }
            else if (lower.contains("测试")) { keywords = "[\"测试\",\"test\",\"qa\"]"; jobType = "TEST"; }
            else if (lower.contains("算法")) { keywords = "[\"算法\",\"ai\",\"机器学习\"]"; jobType = "ALGORITHM"; }
            else if (lower.contains("python")) { keywords = "[\"python\",\"django\",\"flask\"]"; jobType = ""; }

            return "{\"intent\":\"search\",\"keywords\":" + keywords + ",\"city\":\"\",\"company\":\"\",\"jobType\":\"" + jobType + "\"}";
        }

        // 闲聊
        return "{\"intent\":\"chat\",\"keywords\":[],\"city\":\"\",\"company\":\"\",\"jobType\":\"\"}";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 工具结果处理 ====================

    private String generateToolResponse(String query) {
        try {
            String toolName = extractToolName(query);
            String dataJson = extractDataJson(query);

            if (toolName == null) return "抱歉，处理出现了问题。";

            // recommend 特殊处理
            if ("recommend".equals(toolName)) {
                return generateRecommendResponse(query);
            }

            if (dataJson == null) return "抱歉，数据获取失败。";
            JsonNode data = objectMapper.readTree(dataJson);

            switch (toolName) {
                case "search_jobs": return generateSearchResponse(data);
                case "calculate_match": return generateMatchResponse(data);
                case "get_job_detail": return generateDetailResponse(data);
                case "get_company_jobs": return generateCompanyJobsResponse(data);
                default: return "处理完成。";
            }
        } catch (Exception e) {
            log.error("解析工具结果失败", e);
            return "数据处理出错，请稍后再试。";
        }
    }

    // ==================== 搜索结果 ====================

    private String generateSearchResponse(JsonNode data) {
        JsonNode list = data.get("list");
        int total = data.has("total") ? data.get("total").asInt() : 0;

        if (list == null || list.size() == 0 || total == 0) {
            return "🔍 抱歉，暂未找到匹配的岗位。\n\n💡 建议换个关键词或扩大搜索范围。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("为您找到 ").append(total).append(" 个岗位：\n\n");

        int count = Math.min(list.size(), 5);
        for (int i = 0; i < count; i++) {
            JsonNode job = list.get(i);
            String title = getTextField(job, "title", "职位");
            String company = getTextField(job, "company", "公司");
            String city = getTextField(job, "city", "");
            String salary = getTextField(job, "salary", "面议");
            String exp = getTextField(job, "experience", "");
            String edu = getTextField(job, "education", "");

            sb.append(i + 1).append(". 【").append(title).append("】@ ").append(company);
            sb.append(" 💰 ").append(salary);
            if (!city.isEmpty()) sb.append(" 📍 ").append(city);
            sb.append(" 📋 ");
            if (!exp.isEmpty()) sb.append(exp);
            if (!exp.isEmpty() && !edu.isEmpty()) sb.append(" | ");
            if (!edu.isEmpty()) sb.append(edu);
            sb.append("\n");
        }

        sb.append("\n💡 如需查看某个岗位的详情或匹配度，请告诉我岗位名称或编号。");
        return sb.toString();
    }

    // ==================== 匹配度 ====================

    private String generateMatchResponse(JsonNode data) {
        if (data.has("error")) return "❌ " + data.get("error").asText();

        int score = data.has("matchScore") ? data.get("matchScore").asInt() : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("📊 匹配度分析：").append(score).append("%\n\n");

        JsonNode matched = data.get("matchedSkills");
        if (matched != null && matched.size() > 0) {
            sb.append("✅ 匹配的技能：\n");
            for (JsonNode s : matched) sb.append("  - ").append(s.asText()).append("\n");
            sb.append("\n");
        }

        JsonNode missing = data.get("missingSkills");
        if (missing != null && missing.size() > 0) {
            sb.append("❌ 缺失的技能：\n");
            for (JsonNode s : missing) sb.append("  - ").append(s.asText()).append("\n");
            sb.append("\n");
        }

        boolean expMatch = data.has("experienceMatch") && data.get("experienceMatch").asBoolean();
        sb.append(expMatch ? "✅" : "❌").append(" 经验匹配");
        if (data.has("userYears") && data.has("jobExperience")) {
            sb.append("：您有").append(data.get("userYears").asInt()).append("年，岗位要求").append(data.get("jobExperience").asInt()).append("年");
        }
        sb.append("\n");

        boolean eduMatch = data.has("educationMatch") && data.get("educationMatch").asBoolean();
        sb.append(eduMatch ? "✅" : "❌").append(" 学历匹配\n");

        sb.append("\n💡 建议：");
        if (score >= 80) sb.append("匹配度很高，建议尽快投递！");
        else if (score >= 60) sb.append("匹配度不错，可以投递试试。");
        else sb.append("匹配度偏低，建议先提升相关技能。");

        return sb.toString();
    }

    // ==================== 岗位详情 ====================

    private String generateDetailResponse(JsonNode data) {
        if (data.has("error")) return "❌ " + data.get("error").asText();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 岗位详情\n\n");
        sb.append("【").append(getTextField(data, "title", "职位")).append("】@ ");
        sb.append(getTextField(data, "company", "公司"));
        sb.append(" 💰 ").append(getTextField(data, "salary", "面议"));
        sb.append(" 📍 ").append(getTextField(data, "city", "")).append("\n\n");

        String jd = getTextField(data, "jdText", "");
        if (!jd.isEmpty()) {
            sb.append("📝 岗位描述：\n").append(jd.length() > 200 ? jd.substring(0, 200) + "..." : jd).append("\n\n");
        }

        JsonNode skills = data.get("skillTags");
        if (skills != null && skills.size() > 0) {
            sb.append("🛠 核心技能要求：\n");
            for (JsonNode s : skills) sb.append("  - ").append(s.asText()).append("\n");
        }

        return sb.toString();
    }

    // ==================== 企业岗位 ====================

    private String generateCompanyJobsResponse(JsonNode data) {
        JsonNode list = data.get("list");
        String company = data.has("company") ? data.get("company").asText() : "该企业";

        if (list == null || list.size() == 0) return "🏢 " + company + "暂无在招岗位。";

        StringBuilder sb = new StringBuilder();
        sb.append("🏢 ").append(company).append(" 在招岗位：\n\n");
        for (int i = 0; i < list.size(); i++) {
            JsonNode job = list.get(i);
            sb.append(i + 1).append(". 【").append(getTextField(job, "title", "职位")).append("】");
            sb.append(" 💰 ").append(getTextField(job, "salary", "面议"));
            sb.append(" 📍 ").append(getTextField(job, "city", "")).append("\n");
        }
        return sb.toString();
    }

    // ==================== 推荐 ====================

    private String generateRecommendResponse(String query) {
        String dataSection = query;
        int dataStart = query.indexOf("数据：");
        int dataEnd = query.indexOf("【用户问题】");
        if (dataStart > 0) {
            dataSection = query.substring(dataStart + 3, dataEnd > dataStart ? dataEnd : query.length());
        }

        String searchResultPart = "";
        int idx = dataSection.indexOf("搜索结果（已按匹配度排序）：");
        if (idx > 0) {
            searchResultPart = dataSection.substring(idx + 13).trim();
        } else {
            idx = dataSection.indexOf("搜索结果：");
            if (idx > 0) searchResultPart = dataSection.substring(idx + 5).trim();
        }

        String resumePart = "";
        int resumeIdx = dataSection.indexOf("简历信息：");
        int searchIdx = dataSection.indexOf("搜索结果");
        if (resumeIdx > 0 && searchIdx > resumeIdx) {
            resumePart = dataSection.substring(resumeIdx + 5, searchIdx).trim();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 根据您的简历为您推荐以下岗位：\n\n");

        // 提取简历技能
        try {
            if (resumePart.contains("\"skills\"")) {
                JsonNode resume = objectMapper.readTree(resumePart);
                if (resume.has("skills")) {
                    sb.append("📋 您的技能：");
                    List<String> skills = new ArrayList<>();
                    for (JsonNode s : resume.get("skills")) skills.add(s.asText());
                    sb.append(String.join(", ", skills)).append("\n\n");
                }
            }
        } catch (Exception ignored) {}

        // 解析岗位列表
        try {
            if (searchResultPart.startsWith("[")) {
                JsonNode list = objectMapper.readTree(searchResultPart);
                if (list.size() > 0) {
                    int count = Math.min(list.size(), 5);
                    for (int i = 0; i < count; i++) {
                        JsonNode job = list.get(i);
                        String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : (i + 1) + "️⃣";
                        int matchScore = job.has("matchScore") ? job.get("matchScore").asInt() : 0;
                        String emoji = matchScore >= 80 ? "🟢" : matchScore >= 60 ? "🟡" : "🔴";

                        sb.append(medal).append(" Top ").append(i + 1).append("：【");
                        sb.append(getTextField(job, "title", "职位")).append("】@ ");
                        sb.append(getTextField(job, "company", "公司"));
                        sb.append(" 💰 ").append(getTextField(job, "salary", "面议"));
                        sb.append(" 📍 ").append(getTextField(job, "city", ""));
                        if (matchScore > 0) sb.append(" ").append(emoji).append(" 匹配度：").append(matchScore).append("%");
                        sb.append("\n\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        sb.append("💡 投递建议：优先投递匹配度85%以上的岗位。");
        return sb.toString();
    }

    // ==================== 工具方法 ====================

    private String extractToolName(String query) {
        int start = query.indexOf("工具：");
        if (start < 0) return null;
        start += 3;
        int end = query.indexOf("\n", start);
        return end > 0 ? query.substring(start, end).trim() : query.substring(start).trim();
    }

    private String extractDataJson(String query) {
        int start = query.indexOf("数据：");
        if (start < 0) return null;
        start += 3;
        int jsonStart = query.indexOf("{", start);
        if (jsonStart < 0) return null;
        int depth = 0;
        int jsonEnd = -1;
        for (int i = jsonStart; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) { jsonEnd = i + 1; break; } }
        }
        return jsonEnd > 0 ? query.substring(jsonStart, jsonEnd) : null;
    }

    private String getTextField(JsonNode node, String field, String defaultVal) {
        if (node.has(field) && !node.get(field).isNull()) return node.get(field).asText();
        return defaultVal;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}

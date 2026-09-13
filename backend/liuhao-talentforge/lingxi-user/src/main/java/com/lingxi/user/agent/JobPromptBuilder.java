package com.lingxi.user.agent;

import org.springframework.stereotype.Component;

/**
 * Job Agent Prompt 组装（混合架构：只发数据给 LLM，不加工具定义）
 * <p>
 * 核心思路：后端控工具调用，LLM 只负责格式化回答。
 * 不给 LLM 发工具定义（不像 function calling），只发工具执行结果 + 用户问题。
 * </p>
 * <p>
 * 两种 Prompt：
 * 1. buildChatQuery(): 主对话 Prompt（工具结果 + 用户问题 → LLM 格式化回答）
 * 2. buildClassifyPrompt(): 意图分类 Prompt（用户消息 → LLM 返回 JSON 分类结果）
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Component
public class JobPromptBuilder {

    /**
     * 组装对话 Prompt
     *
     * @param userMessage 用户消息
     * @param toolName    工具名称（可为 null）
     * @param toolResult  工具执行结果（可为 null）
     * @param history     对话历史
     * @return 完整的 query 文本
     */
    public String buildChatQuery(String userMessage, String toolName,
                                  String toolResult, String history) {
        StringBuilder sb = new StringBuilder();

        // 对话历史
        if (history != null && !history.isEmpty()) {
            sb.append("【对话历史】\n").append(history).append("\n\n");
        }

        // 工具返回数据
        if (toolName != null && toolResult != null && !toolResult.isEmpty()) {
            sb.append("【工具调用结果】\n");
            sb.append("工具：").append(toolName).append("\n");
            sb.append("数据：").append(toolResult).append("\n\n");
        }

        // 用户问题
        sb.append("【用户问题】\n").append(userMessage);

        return sb.toString();
    }

    /**
     * 组装意图分类 Prompt（LLM 兜底识别）
     *
     * @param userMessage 用户消息
     * @return 分类 prompt
     */
    public String buildClassifyPrompt(String userMessage) {
        return "你是一个意图分类助手。根据用户消息，返回一个 JSON 对象，不要返回其他内容。\n\n" +
                "字段说明：\n" +
                "- intent: 意图类型，取值 search/detail/recommend/match/company/chat\n" +
                "- keywords: 搜索关键词列表（中文+英文同义词，如[\"前端\",\"frontend\",\"react\"]），非搜索意图返回空数组\n" +
                "- city: 城市名，没有则为空字符串\n" +
                "- company: 公司名，没有则为空字符串\n" +
                "- jobType: 岗位类型枚举，取值 FRONTEND/JAVA_BACKEND/MOBILE/DATA/ALGORITHM/TEST/DEVOPS/PRODUCT/DESIGN/FULLSTACK/BACKEND，无法判断则为空字符串\n\n" +
                "意图判断规则：\n" +
                "- search: 用户想找/搜/查/看岗位、工作、职位\n" +
                "- detail: 用户想看某个具体岗位的详情/要求/介绍\n" +
                "- recommend: 用户想要推荐/匹配/适合自己的岗位\n" +
                "- match: 用户想知道自己和某个岗位的匹配度/够不够格\n" +
                "- company: 用户想看某个公司的岗位/招聘\n" +
                "- chat: 闲聊/打招呼/与求职无关的问题\n\n" +
                "用户消息：" + userMessage + "\n\n" +
                "返回JSON：";
    }
}

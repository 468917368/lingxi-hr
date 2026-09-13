package com.lingxi.hr.agent;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 三大场景 LLM query 组装（百宝箱 /api/chat 文本输入，工作流从 query 提取参数）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Component
public class MockPromptBuilder {

    /** 出题工作流 query */
    public String buildGenerateQuery(String jobInfo, String resumeInfo, int questionCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为候选人生成").append(questionCount).append("道模拟面试题。\n\n");
        sb.append("【岗位考察要点】\n").append(jobInfo == null || jobInfo.isEmpty() ? "无" : jobInfo).append("\n\n");
        sb.append("【候选人简历画像】\n").append(resumeInfo == null || resumeInfo.isEmpty() ? "无（按岗位通用要求出题）" : resumeInfo).append("\n\n");
        return sb.toString();
    }

    /** 评分工作流 query */
    public String buildScoreQuery(String questionContent, String questionType,
                                  String referenceAnswer, String candidateAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对候选人的单题作答进行评分。\n\n");
        sb.append("【题目】\n").append(questionContent == null ? "" : questionContent).append("\n\n");
        sb.append("【题型】").append(questionType == null ? "" : questionType).append("\n\n");
        sb.append("【参考要点】\n").append(referenceAnswer == null || referenceAnswer.isEmpty() ? "无" : referenceAnswer).append("\n\n");
        sb.append("【候选人作答】\n").append(candidateAnswer == null ? "" : candidateAnswer).append("\n\n");
        sb.append("【评分要求】\n");
        sb.append("1. 技术准确度techAccuracyScore、表达逻辑expressionScore、知识深度knowledgeDepthScore，各 0-100 整数。\n");
        sb.append("2. 作答少于20字 → 各维分数不超过50，并在aiComment中标注\"回答过短\"。\n");
        sb.append("3. aiComment 1-2句话，先肯定亮点再指出不足。\n");
        sb.append("4. 输出严格的 JSON，不要输出任何额外文字：\n");
        sb.append("{\"techAccuracyScore\":82,\"expressionScore\":75,\"knowledgeDepthScore\":68,\"aiComment\":\"...\"}");
        return sb.toString();
    }

    /** 报告工作流 query */
    public String buildReportQuery(String jobInfo, String questionsSummary, BigDecimal overallScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为候选人复盘一次模拟面试。\n\n");
        sb.append("【岗位考察要点】\n").append(jobInfo == null || jobInfo.isEmpty() ? "无" : jobInfo).append("\n\n");
        sb.append("【答题汇总】\n").append(questionsSummary == null || questionsSummary.isEmpty() ? "无" : questionsSummary).append("\n\n");
        sb.append("【综合得分】").append(overallScore == null ? "0" : overallScore.toPlainString()).append("\n\n");
        sb.append("【报告要求】\n");
        sb.append("1. highlights 亮点 2-3 条，基于高分维度或优秀作答。\n");
        sb.append("2. weaknesses 短板 2-3 条，基于低分维度或暴露问题。\n");
        sb.append("3. improvementPlan 提升方案 3-5 条，具体可执行。\n");
        sb.append("4. 输出严格的 JSON，不要输出任何额外文字：\n");
        sb.append("{\"highlights\":[\"...\"],\"weaknesses\":[\"...\"],\"improvementPlan\":[\"...\"]}");
        return sb.toString();
    }
}

package com.lingxi.hr.agent.impl;

import com.lingxi.hr.agent.LlmClient;
import com.lingxi.hr.agent.config.BaibaoxiangProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mock LLM 客户端（联调/演示模式）
 * <p>按 appId 区分场景返回预设 JSON（出题/评分/报告），模拟百宝箱工作流输出，用于无 token 环境自测全链路。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hr.agent.mock", havingValue = "true")
public class MockTboxLlmClient implements LlmClient {

    private final BaibaoxiangProperties properties;

    /** 预设通用题目（5 道，覆盖 4 题型） */
    private static final String MOCK_QUESTIONS =
            "{\"questions\":[" +
            "{\"questionNumber\":1,\"questionType\":\"BASIC\",\"dimension\":\"基础验证\",\"difficulty\":\"EASY\",\"content\":\"请解释进程与线程的区别。\"}," +
            "{\"questionNumber\":2,\"questionType\":\"BASIC\",\"dimension\":\"基础验证\",\"difficulty\":\"MEDIUM\",\"content\":\"请解释闭包的概念，并说明其常见用途与潜在的内存泄漏风险。\"}," +
            "{\"questionNumber\":3,\"questionType\":\"PROJECT\",\"dimension\":\"项目深挖\",\"difficulty\":\"HARD\",\"content\":\"描述一次你解决过的线上性能问题，从定位到解决的全过程。\"}," +
            "{\"questionNumber\":4,\"questionType\":\"BOUNDARY\",\"dimension\":\"能力边界\",\"difficulty\":\"HARD\",\"content\":\"假设系统需要支持每秒十万次写入，你会如何设计存储方案？\"}," +
            "{\"questionNumber\":5,\"questionType\":\"COMPREHENSIVE\",\"dimension\":\"综合素养\",\"difficulty\":\"MEDIUM\",\"content\":\"当你的技术方案与团队主流意见冲突时，你会如何处理？\"}" +
            "]}";

    /** 预设单题评分 */
    private static final String MOCK_SCORE =
            "{\"techAccuracyScore\":82,\"expressionScore\":75,\"knowledgeDepthScore\":68,\"aiComment\":\"回答基本正确，概念清晰；可进一步深入到原理与源码层面展开。\"}";

    /** 预设面试报告 */
    private static final String MOCK_REPORT =
            "{\"highlights\":[\"基础概念掌握扎实\",\"作答结构清晰\"]," +
            "\"weaknesses\":[\"知识深度有待加强\",\"边界场景应变不足\"]," +
            "\"improvementPlan\":[\"阅读相关框架源码并输出技术笔记\",\"练习限时作答培养节奏感\",\"学习 STAR 法则组织面试表达\"]}";

    @Override
    public String generate(String appId, String query, String userId, long timeoutMs) {
        log.info("[MockLlm] appId={}, query={}", appId, truncate(query, 200));
        String generateAppId = properties.getAgent().getGenerateAppId();
        String scoreAppId = properties.getAgent().getScoreAppId();
        String reportAppId = properties.getAgent().getReportAppId();

        if (generateAppId != null && generateAppId.equals(appId)) {
            return MOCK_QUESTIONS;
        }
        if (scoreAppId != null && scoreAppId.equals(appId)) {
            return MOCK_SCORE;
        }
        if (reportAppId != null && reportAppId.equals(appId)) {
            return MOCK_REPORT;
        }
        log.warn("[MockLlm] 未知 appId={}，返回空对象", appId);
        return "{}";
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

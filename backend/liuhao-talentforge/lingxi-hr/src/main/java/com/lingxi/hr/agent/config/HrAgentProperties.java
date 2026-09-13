package com.lingxi.hr.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mock Interview Agent 配置
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
@Component
@ConfigurationProperties(prefix = "hr.agent")
public class HrAgentProperties {

    /** true=MockTboxLlmClient（预设 JSON）；false=真实百宝箱 TboxLlmClient */
    private boolean mock = true;

    private Llm llm = new Llm();

    @Data
    public static class Llm {
        /** 单次 LLM 生成整体超时（ms），默认 60s */
        private long timeoutMs = 60000;

        /** 单题评分超时（ms），默认 15s */
        private long scoreTimeoutMs = 15000;
    }
}

package com.lingxi.user.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Job Agent 配置
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "job-agent")
public class JobAgentProperties {

    /** true=Mock 模式；false=真实百宝箱 */
    private boolean mock = true;

    private Llm llm = new Llm();

    @Data
    public static class Llm {
        /** 单次 LLM 调用超时（ms），默认 60s */
        private long timeoutMs = 60000;
    }
}

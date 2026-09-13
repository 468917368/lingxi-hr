package com.lingxi.hr.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百宝箱配置（appId + token）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
@Component
@ConfigurationProperties(prefix = "baibaoxiang")
public class BaibaoxiangProperties {

    private Api api = new Api();

    private Agent agent = new Agent();

    @Data
    public static class Api {
        /** 访问令牌（一个账号一个，三个工作流共用） */
        private String key;
    }

    @Data
    public static class Agent {
        /** 出题工作流 appId */
        private String generateAppId;
        /** 评分工作流 appId */
        private String scoreAppId;
        /** 报告工作流 appId */
        private String reportAppId;
    }
}

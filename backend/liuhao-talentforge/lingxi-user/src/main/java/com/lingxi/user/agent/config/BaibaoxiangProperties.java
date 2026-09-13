package com.lingxi.user.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百宝箱配置（appId + key）
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "baibaoxiang")
public class BaibaoxiangProperties {

    private Api api = new Api();
    private Agent agent = new Agent();

    @Data
    public static class Api {
        /** 访问令牌 */
        private String key;
    }

    @Data
    public static class Agent {
        /** Job Agent 工作流 appId */
        private String appId;
    }
}

package com.lingxi.hr.agent.config;

import cn.tbox.sdk.TboxClient;
import cn.tbox.sdk.core.exception.TboxClientConfigException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 百宝箱 TboxClient Bean（仅真实模式注入）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Configuration
@RequiredArgsConstructor
public class TboxClientConfig {

    private final BaibaoxiangProperties properties;

    @Bean
    @ConditionalOnProperty(name = "hr.agent.mock", havingValue = "false")
    public TboxClient tboxClient() {
        try {
            return new TboxClient(properties.getApi().getKey());
        } catch (TboxClientConfigException e) {
            throw new IllegalStateException("百宝箱 TboxClient 初始化失败", e);
        }
    }
}

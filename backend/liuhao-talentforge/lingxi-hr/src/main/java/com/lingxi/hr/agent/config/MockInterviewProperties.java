package com.lingxi.hr.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模拟面试业务配置（次数限制等）
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "hr.mock-interview")
public class MockInterviewProperties {

    /** 每日每求职者模拟面试次数上限，0=不限（默认） */
    private int quota = 0;
}

package com.lingxi.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文加载测试
 * <p>跳过 Nacos bootstrap，仅验证 Spring 上下文与本地 MySQL 连接正常。</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
class JobApplicationTest {

    @Test
    void contextLoads() {
    }
}

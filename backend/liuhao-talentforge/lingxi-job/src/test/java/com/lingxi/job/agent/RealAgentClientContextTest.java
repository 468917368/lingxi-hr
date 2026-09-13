package com.lingxi.job.agent;

import com.lingxi.job.agent.impl.RealBaibaoxiangAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real 百宝箱客户端上下文测试（ai.agent.mock=false）
 * <p>验证 mock=false 时 {@code BaibaoxiangAgentClient} 唯一 Bean 且为 Real 实现、应用可启动
 * （不发起真实网络调用，仅 Bean 装配断言）。Real 模式启动校验要求 Token/两个 AppID/HMAC
 * 密钥非空，故注入脱敏的虚拟配置（仅测试用，非真实密钥）。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@SpringBootTest(properties = {
        "spring.cloud.bootstrap.enabled=false",
        "ai.agent.mock=false",
        "baibaoxiang.authorization=test-authorization",
        "baibaoxiang.user-id-hmac-key=test-hmac-key",
        "baibaoxiang.jd-parse-app-id=test-jd-app-id",
        "baibaoxiang.interview-app-id=test-interview-app-id"
})
class RealAgentClientContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void realClientUniqueAndStartable() {
        BaibaoxiangAgentClient client = applicationContext.getBean(BaibaoxiangAgentClient.class);
        assertNotNull(client);
        assertTrue(client instanceof RealBaibaoxiangAgentClient,
                "ai.agent.mock=false 时应装配 RealBaibaoxiangAgentClient");
    }
}

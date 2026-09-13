package com.lingxi.job.agent;

import com.lingxi.job.agent.impl.MockBaibaoxiangAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock 百宝箱客户端上下文测试（test Profile：ai.agent.mock=true）
 * <p>验证 test Profile 下 {@code BaibaoxiangAgentClient} 唯一 Bean 且为 Mock 实现
 * （不发起真实网络调用），保证自动化测试固定使用 Mock。Mock/Real 双实现按
 * {@code ai.agent.mock} 切换，Real 模式对照见 {@link RealAgentClientContextTest}。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@ActiveProfiles("test")
class MockAgentClientContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testProfile_uniqueMockClient() {
        Map<String, BaibaoxiangAgentClient> beans = applicationContext.getBeansOfType(BaibaoxiangAgentClient.class);
        assertEquals(1, beans.size(), "test Profile 下 BaibaoxiangAgentClient 应唯一装配");
        BaibaoxiangAgentClient client = beans.values().iterator().next();
        assertNotNull(client);
        assertTrue(client instanceof MockBaibaoxiangAgentClient,
                "test Profile 下应装配 MockBaibaoxiangAgentClient，实际: " + client.getClass().getName());
    }
}

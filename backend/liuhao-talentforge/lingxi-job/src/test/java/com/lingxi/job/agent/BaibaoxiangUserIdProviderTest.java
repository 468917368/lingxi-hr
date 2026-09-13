package com.lingxi.job.agent;

import com.lingxi.job.agent.impl.MockBaibaoxiangUserIdProvider;
import com.lingxi.job.agent.impl.RealBaibaoxiangUserIdProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 百宝箱 userId 伪标识提供者单测
 * <p>覆盖 Real HMAC 稳定性、AppID 隔离、不回显内部用户 ID；Mock 固定值且不读取密钥。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
class BaibaoxiangUserIdProviderTest {

    @Test
    void real_sameUserAndApp_stable() {
        // 同一内部用户 + 同一 AppID → HMAC 结果稳定
        RealBaibaoxiangUserIdProvider provider = new RealBaibaoxiangUserIdProvider("test-hmac-key");
        String a1 = provider.provide(1001L, "app-a");
        String a2 = provider.provide(1001L, "app-a");
        assertEquals(a1, a2);
    }

    @Test
    void real_differentApp_different() {
        // 不同 AppID → 结果不同（应用间数据隔离）
        RealBaibaoxiangUserIdProvider provider = new RealBaibaoxiangUserIdProvider("test-hmac-key");
        assertNotEquals(provider.provide(1001L, "app-a"), provider.provide(1001L, "app-b"));
    }

    @Test
    void real_differentUser_different() {
        // 不同内部用户 → 结果不同
        RealBaibaoxiangUserIdProvider provider = new RealBaibaoxiangUserIdProvider("test-hmac-key");
        assertNotEquals(provider.provide(1001L, "app-a"), provider.provide(1002L, "app-a"));
    }

    @Test
    void real_hex64_noInternalIdLeak() {
        // 输出为 SHA-256 十六进制（64 位），不回显原始内部用户 ID
        RealBaibaoxiangUserIdProvider provider = new RealBaibaoxiangUserIdProvider("test-hmac-key");
        String result = provider.provide(1001L, "app-a");
        assertTrue(result.matches("[0-9a-f]{64}"), "应输出 64 位十六进制，实际: " + result);
        assertNotEquals("1001", result);
    }

    @Test
    void mock_fixedNoKeyRequired() {
        // Mock 模式返回固定伪标识，不读取真实密钥、与内部用户/AppID 无关
        MockBaibaoxiangUserIdProvider provider = new MockBaibaoxiangUserIdProvider();
        assertEquals("local-mock-user", provider.provide(1001L, "app-a"));
        assertEquals(provider.provide(1L, "app-x"), provider.provide(2L, "app-y"));
    }
}

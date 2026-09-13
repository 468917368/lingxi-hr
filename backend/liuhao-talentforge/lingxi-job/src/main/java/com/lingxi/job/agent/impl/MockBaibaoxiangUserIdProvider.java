package com.lingxi.job.agent.impl;

import com.lingxi.job.agent.BaibaoxiangUserIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 百宝箱 userId 提供者（默认实现，仅限本地测试）
 * <p>
 * {@code ai.agent.mock=true}（缺省默认）时生效，返回固定伪标识：
 * <ul>
 *   <li>不读取真实 HMAC 密钥，不要求任何真实百宝箱配置</li>
 *   <li>固定值与内部用户 ID/AppID 无关，绝不出站、不产生可外发标识</li>
 * </ul>
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Component
@ConditionalOnProperty(name = "ai.agent.mock", havingValue = "true", matchIfMissing = true)
public class MockBaibaoxiangUserIdProvider implements BaibaoxiangUserIdProvider {

    /** 仅限本地测试的固定 mock 伪标识 */
    private static final String MOCK_USER_ID = "local-mock-user";

    @Override
    public String provide(Long internalUserId, String appId) {
        return MOCK_USER_ID;
    }
}

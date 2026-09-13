package com.lingxi.job.agent.impl;

import com.lingxi.job.agent.BaibaoxiangUserIdProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Real 百宝箱 userId 提供者（{@code ai.agent.mock=false} 生效）
 * <p>
 * 使用服务端 HMAC 密钥计算 {@code HMAC-SHA256(内部用户ID + ":" + 目标AppID)} 的十六进制摘要：
 * <ul>
 *   <li>同一内部用户 + 同一 AppID → 结果稳定（可用于会话关联）</li>
 *   <li>不同 AppID → 结果不同（应用间数据隔离）</li>
 *   <li>不回显内部用户 ID、手机号或账号名</li>
 * </ul>
 * 密钥仅来自环境变量/部署密钥（{@code baibaoxiang.user-id-hmac-key}），不硬编码、不入日志。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.agent.mock", havingValue = "false")
public class RealBaibaoxiangUserIdProvider implements BaibaoxiangUserIdProvider {

    private final String hmacKey;

    public RealBaibaoxiangUserIdProvider(@Value("${baibaoxiang.user-id-hmac-key:}") String hmacKey) {
        this.hmacKey = hmacKey;
    }

    @Override
    public String provide(Long internalUserId, String appId) {
        if (!StringUtils.hasText(hmacKey)) {
            throw new IllegalStateException("百宝箱 userId HMAC 密钥未配置（baibaoxiang.user-id-hmac-key）");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((internalUserId + ":" + appId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("百宝箱 userId HMAC 计算失败", e);
            throw new IllegalStateException("百宝箱 userId HMAC 计算失败", e);
        }
    }
}

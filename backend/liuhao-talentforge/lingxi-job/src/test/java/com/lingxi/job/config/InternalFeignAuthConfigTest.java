package com.lingxi.job.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Feign 出站认证拦截器单测
 * <p>验证 X-Caller-Service / X-Service-Token 头注入（token 缺失时只注入 caller）。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
class InternalFeignAuthConfigTest {

    @Test
    void interceptor_setsCallerAndTokenHeaders() {
        ServicePermissionConfig config = new ServicePermissionConfig();
        Map<String, String> tokens = new HashMap<>();
        tokens.put("lingxi-job", "job-token-dev");
        config.setTokens(tokens);

        RequestInterceptor interceptor = new InternalFeignAuthConfig().internalFeignAuthInterceptor(config);
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertEquals("lingxi-job", first(template.headers(), "X-Caller-Service"));
        assertEquals("job-token-dev", first(template.headers(), "X-Service-Token"));
    }

    @Test
    void interceptor_tokenAbsent_omitsTokenHeader() {
        ServicePermissionConfig config = new ServicePermissionConfig();
        config.setTokens(new HashMap<>());

        RequestInterceptor interceptor = new InternalFeignAuthConfig().internalFeignAuthInterceptor(config);
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertEquals("lingxi-job", first(template.headers(), "X-Caller-Service"));
        assertNull(template.headers().get("X-Service-Token"));
    }

    private String first(Map<String, Collection<String>> headers, String name) {
        Collection<String> values = headers.get(name);
        assertNotNull(values, "缺少请求头: " + name);
        return values.iterator().next();
    }
}

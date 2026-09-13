package com.lingxi.job.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部服务认证拦截器测试
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@SpringBootTest(properties = "spring.cloud.bootstrap.enabled=false")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "service-auth.tokens.lingxi-user=user-token-dev",
        "service-auth.tokens.lingxi-resume=resume-token-dev",
        "service-auth.tokens.lingxi-hr=hr-token-dev",
        "service-auth.tokens.lingxi-admin=admin-token-dev"
})
class InternalAuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 无凭证 → 401 + code:2001
     */
    @Test
    void withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/internal/jobs/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }

    /**
     * 错误 Token → 401 + code:2001
     */
    @Test
    void wrongToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/jobs/1")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }

    /**
     * 有凭证但无接口权限（hr 调 search）→ 403 + code:2002
     */
    @Test
    void noPermission_returns403() throws Exception {
        mockMvc.perform(get("/internal/jobs/search")
                        .header("X-Caller-Service", "lingxi-hr")
                        .header("X-Service-Token", "hr-token-dev"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
    }
}

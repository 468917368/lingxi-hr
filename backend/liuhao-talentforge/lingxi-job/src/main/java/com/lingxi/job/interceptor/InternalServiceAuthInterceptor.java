package com.lingxi.job.interceptor;

import com.lingxi.common.domain.Result;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.config.ServicePermissionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 内部服务认证拦截器
 * <p>
 * 校验 /internal/** 请求携带 X-Caller-Service 与 X-Service-Token：
 * 1. 缺凭证或 Token 不匹配 → HTTP 401 + code:2001
 * 2. 有凭证但无接口权限 → HTTP 403 + code:2002
 * 3. /internal/agent/** 由 X-Run-Token 校验（阶段4），此处放行
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalServiceAuthInterceptor implements HandlerInterceptor {

    private final ServicePermissionConfig permissionConfig;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String caller = request.getHeader("X-Caller-Service");
        String token = request.getHeader("X-Service-Token");

        // 缺凭证或凭证不匹配 → 401
        if (!StringUtils.hasText(caller) || !StringUtils.hasText(token)
                || !token.equals(permissionConfig.getTokens().get(caller))) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    Result.error(2001, "服务身份缺失"));
            return false;
        }

        // 权限校验 → 403
        String uri = request.getRequestURI();
        if (!permissionConfig.canAccess(caller, uri)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    Result.error(2002, "调用服务无权限"));
            return false;
        }
        return true;
    }

    /**
     * 写入统一 JSON 响应
     */
    private void writeJson(HttpServletResponse response, int status, Result<?> result) {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(JsonUtil.toJson(result));
        } catch (Exception e) {
            log.error("写服务认证失败响应出错", e);
        }
    }
}

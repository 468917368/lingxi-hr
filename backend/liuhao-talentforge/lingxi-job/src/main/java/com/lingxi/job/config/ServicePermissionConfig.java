package com.lingxi.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 内部服务凭证与权限矩阵配置
 * <p>
 * 绑定 application-dev.yml 中 service-auth.tokens（服务名 → 凭证Token）。
 * 权限矩阵来源：系分文档「服务身份认证（B-05）」。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "service-auth")
public class ServicePermissionConfig {

    /** 服务名 → 凭证Token */
    private Map<String, String> tokens = new HashMap<>();

    /** 服务名 → 允许访问的内部 URI 正则模式 */
    private static final Map<String, List<Pattern>> PERMISSION_MATRIX = new HashMap<>();

    static {
        // 使用正则精确匹配：{jobId} 必须是纯数字，避免 /search 等被 {jobId} 误匹配
        PERMISSION_MATRIX.put("lingxi-user", Arrays.asList(
                Pattern.compile("^/internal/jobs/[0-9]+$"),
                Pattern.compile("^/internal/jobs/search$"),
                Pattern.compile("^/internal/jobs/[0-9]+/requirements$"),
                Pattern.compile("^/internal/jobs/company/[0-9]+$")));
        PERMISSION_MATRIX.put("lingxi-resume", Arrays.asList(
                Pattern.compile("^/internal/jobs/[0-9]+$")));
        PERMISSION_MATRIX.put("lingxi-hr", Arrays.asList(
                Pattern.compile("^/internal/jobs/[0-9]+$"),
                Pattern.compile("^/internal/jobs/[0-9]+/requirements$"),
                Pattern.compile("^/internal/jobs/company/[0-9]+$"),
                Pattern.compile("^/internal/jobs/[0-9]+/hc/.*$")));
        PERMISSION_MATRIX.put("lingxi-admin", Arrays.asList(
                Pattern.compile("^/internal/jobs/[0-9]+$"),
                Pattern.compile("^/internal/jobs/statistics/.*$"),
                Pattern.compile("^/internal/admin/jobs.*$")));
    }

    /**
     * 校验调用服务是否有权访问指定内部 URI
     *
     * @param caller 调用方服务名
     * @param uri    请求 URI（不含 context-path）
     * @return true=有权，false=无权
     */
    public boolean canAccess(String caller, String uri) {
        List<Pattern> patterns = PERMISSION_MATRIX.get(caller);
        if (patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(p -> p.matcher(uri).matches());
    }
}

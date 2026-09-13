package com.lingxi.common.interceptor;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.regex.Pattern;

/**
 * 数据权限拦截器
 * <p>
 * 根据用户角色自动追加SQL条件，实现行级数据隔离：
 * - CANDIDATE: 只看自己的数据 (candidate_id = #{userId})
 * - HR: 只看本企业数据 (company_id = #{companyId})
 * - INTERVIEWER: 只看被分配的数据 (interviewer_id = #{userId})
 * - ADMIN: 查看所有数据（不追加条件）
 * </p>
 * <p>
 * 通过 {@code data.permission.enabled} 配置开关控制，默认关闭。
 * 开启后需要确保查询的表包含对应的权限字段（candidate_id/company_id/interviewer_id），
 * 否则会SQL报错。建议配合数据权限注解精细控制。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DataPermissionInterceptor implements Interceptor {

    /** 数据权限总开关，默认关闭 */
    @Value("${data.permission.enabled:false}")
    private boolean enabled;

    /** 需要跳过数据权限的 SQL 关键字（内部调用、统计等） */
    private static final Pattern SKIP_PATTERN = Pattern.compile(
            "\\b(internal|count|exists|sum|avg|max|min)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 开关关闭，直接放行
        if (!enabled) {
            return invocation.proceed();
        }

        UserDTO user = UserContext.get();
        // 没有用户上下文（内部调用、异步线程等），直接放行
        if (user == null) {
            return invocation.proceed();
        }

        // ADMIN 不限制
        if (user.isAdmin()) {
            return invocation.proceed();
        }

        // 获取原始 SQL
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();

        // 跳过不需要拦截的 SQL（内部调用、聚合查询等）
        if (skip(originalSql)) {
            return invocation.proceed();
        }

        // 根据角色生成数据权限条件
        String condition = buildCondition(user);
        if (condition == null) {
            return invocation.proceed();
        }

        // 用子查询包装，避免原有 ORDER BY / LIMIT 干扰
        String newSql = "SELECT _dp_.* FROM (" + originalSql + ") _dp_ WHERE " + condition;

        // 反射替换 SQL
        Field sqlField = BoundSql.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(boundSql, newSql);

        log.debug("数据权限拦截: role={}, userId={}, companyId={}, 原始SQL长度={}, 新SQL长度={}",
                user.getRole(), user.getUserId(), user.getCompanyId(),
                originalSql.length(), newSql.length());

        return invocation.proceed();
    }

    /**
     * 根据用户角色生成 WHERE 条件
     *
     * @return 条件字符串，返回 null 表示不追加
     */
    private String buildCondition(UserDTO user) {
        switch (user.getRole()) {
            case "CANDIDATE":
                if (user.getUserId() == null) return null;
                return "candidate_id = " + user.getUserId();
            case "HR":
                if (user.getCompanyId() == null) return null;
                return "company_id = " + user.getCompanyId();
            case "INTERVIEWER":
                if (user.getUserId() == null) return null;
                return "interviewer_id = " + user.getUserId();
            default:
                return null;
        }
    }

    /**
     * 判断是否跳过拦截
     */
    private boolean skip(String sql) {
        // 跳过内部调用标记的 SQL
        if (sql.contains("/*no-permission*/")) {
            return true;
        }
        // 跳过聚合查询（统计报表等）
        return SKIP_PATTERN.matcher(sql).find();
    }
}

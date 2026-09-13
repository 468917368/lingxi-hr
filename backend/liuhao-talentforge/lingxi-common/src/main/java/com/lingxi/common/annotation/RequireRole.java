package com.lingxi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要特定角色注解
 *
 * <p>支持方法级别和类级别，方法级别优先于类级别。</p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * 允许的角色列表
     */
    String[] value();
}

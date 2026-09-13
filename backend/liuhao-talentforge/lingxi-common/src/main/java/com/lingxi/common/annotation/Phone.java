package com.lingxi.common.annotation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 手机号格式校验注解
 * <p>
 * 校验规则：^1[3-9]\d{9}$（11位，1开头，第二位3-9）
 * 消除各DTO中重复的 @Pattern 正则。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
public @interface Phone {

    String message() default "手机号格式不正确";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

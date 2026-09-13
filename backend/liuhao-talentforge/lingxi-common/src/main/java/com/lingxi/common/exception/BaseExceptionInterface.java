package com.lingxi.common.exception;

/**
 * 异常码接口
 * <p>
 * 各模块的错误码枚举需实现此接口。
 * 使用方式：
 * <pre>
 * // 定义错误码枚举
 * public enum UserErrorCode implements BaseExceptionInterface {
 *     PHONE_INVALID(1001, "手机号格式不正确"),
 *     CODE_EXPIRED(1002, "验证码已过期");
 * }
 *
 * // 抛出异常
 * throw new BusinessException(UserErrorCode.PHONE_INVALID);
 * </pre>
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public interface BaseExceptionInterface {

    /**
     * 获取异常码
     */
    int getErrorCode();

    /**
     * 获取异常信息
     */
    String getErrorMessage();
}

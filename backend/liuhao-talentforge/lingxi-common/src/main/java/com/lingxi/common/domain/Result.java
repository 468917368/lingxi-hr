package com.lingxi.common.domain;

import com.lingxi.common.constant.CommonConstant;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一API响应
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码，200表示成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(CommonConstant.SUCCESS_CODE, CommonConstant.SUCCESS_MESSAGE, null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(CommonConstant.SUCCESS_CODE, CommonConstant.SUCCESS_MESSAGE, data);
    }

    /**
     * 成功响应（带数据和消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(CommonConstant.SUCCESS_CODE, message, data);
    }

    /**
     * 错误响应
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 错误响应（通过异常接口）
     */
    public static <T> Result<T> error(com.lingxi.common.exception.BaseExceptionInterface exception) {
        return new Result<>(exception.getErrorCode(), exception.getErrorMessage(), null);
    }

    /**
     * 系统错误
     */
    public static <T> Result<T> systemError() {
        return new Result<>(CommonConstant.SYSTEM_ERROR_CODE, CommonConstant.SYSTEM_ERROR_MESSAGE, null);
    }

    /**
     * 系统错误（自定义消息）
     */
    public static <T> Result<T> systemError(String message) {
        return new Result<>(CommonConstant.SYSTEM_ERROR_CODE, message, null);
    }

    /**
     * 参数错误
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 未授权
     */
    public static <T> Result<T> unauthorized() {
        return new Result<>(401, "未授权", null);
    }

    /**
     * 未授权（自定义消息）
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /**
     * 禁止访问
     */
    public static <T> Result<T> forbidden() {
        return new Result<>(403, "禁止访问", null);
    }

    /**
     * 禁止访问（自定义消息）
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /**
     * 资源不存在
     */
    public static <T> Result<T> notFound() {
        return new Result<>(404, "资源不存在", null);
    }

    /**
     * 资源不存在（自定义消息）
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code == CommonConstant.SUCCESS_CODE;
    }
}

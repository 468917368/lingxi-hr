package com.lingxi.resume.exception;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 简历服务模块级异常补充处理
 *
 * <p>处理 common 全局异常处理器未覆盖的场景：
 * 上传文件超过 Spring multipart 限制时（进入 Controller 前拦截）抛出
 * {@link MaxUploadSizeExceededException}，统一转换为 3002 文件过大错误码。
 *
 * @author 成员C
 * @since 2026-08-02
 */
@Slf4j
@RestControllerAdvice
public class ResumeExceptionHandler {

    /**
     * 上传文件超限（multipart 层拦截，未进入业务校验）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限: {}", e.getMessage());
        return Result.error(ResumeErrorCode.FILE_TOO_LARGE.getErrorCode(),
                ResumeErrorCode.FILE_TOO_LARGE.getErrorMessage());
    }
}

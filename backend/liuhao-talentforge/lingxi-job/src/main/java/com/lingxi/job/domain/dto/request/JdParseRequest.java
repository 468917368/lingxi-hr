package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * JD 解析请求 DTO
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class JdParseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JD 原文 */
    @NotBlank(message = "JD文本不能为空")
    @Size(max = 20000, message = "JD文本长度不能超过20000")
    private String jdText;
}

package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 标记候选人合适/不合适入参
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
public class MarkCandidateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作：SUITABLE=合适 UNSUITABLE=不合适 */
    @NotBlank(message = "action 不能为空")
    @Pattern(regexp = "SUITABLE|UNSUITABLE", message = "action 仅支持 SUITABLE / UNSUITABLE")
    private String action;
}

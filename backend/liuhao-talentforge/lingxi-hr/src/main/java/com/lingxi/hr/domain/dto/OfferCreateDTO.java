package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 发起 Offer 入参（系分 5.5.4）
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class OfferCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    @NotNull(message = "applicationId不能为空")
    private Long applicationId;

    /** 月薪（元，整数，>0） */
    @NotNull(message = "salary不能为空")
    @Min(value = 1, message = "salary必须大于0")
    private Integer salary;

    /** 预计入职日期（必须为未来日期） */
    @NotNull(message = "entryDate不能为空")
    private LocalDate entryDate;

    /** 职级（如 P6、高级工程师） */
    private String level;

    /** 备注 */
    private String remark;

    /** 有效期天数（默认 3，1~30） */
    private Integer expiresInDays;
}

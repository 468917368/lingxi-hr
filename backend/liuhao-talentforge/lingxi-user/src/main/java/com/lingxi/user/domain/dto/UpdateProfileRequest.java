package com.lingxi.user.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 更新求职意向请求
 * <p>
 * 对齐前端 services/user.ts UpdateProfileRequest
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class UpdateProfileRequest {

    /** 期望岗位 */
    @Size(max = 64, message = "期望职位名称过长")
    private String desiredJob;

    /** 期望城市 */
    @Size(max = 128, message = "期望城市名称过长")
    private String desiredCity;

    /** 期望最低薪资 */
    private Integer desiredSalaryMin;

    /** 期望最高薪资 */
    private Integer desiredSalaryMax;

    /** 到岗时间（yyyy-MM-dd格式） */
    private LocalDate availableFrom;
}

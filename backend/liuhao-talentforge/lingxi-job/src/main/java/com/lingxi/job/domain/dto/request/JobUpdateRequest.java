package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Future;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位编辑请求
 * <p>独立 DTO 不继承创建请求；画像三件套（profile/profileVersion/profileConfirmed）不标 @NotNull，
 * 三件套配对校验在 Service 层做（全缺=不改画像，全出现=更新画像，部分出现=400）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位名称 */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称不能超过100字符")
    private String title;

    /** 岗位一级行业编码 */
    @NotBlank(message = "行业分组编码不能为空")
    private String industryGroupCode;

    /** 岗位具体行业编码 */
    @NotBlank(message = "行业编码不能为空")
    private String industryCode;

    /** 城市编码 */
    @NotBlank(message = "城市编码不能为空")
    private String cityCode;

    /** 城市展示名称（可空：后端以 cityCode 为唯一真值，按城市字典回填；保留字段兼容旧前端） */
    @Size(max = 64, message = "城市名称不能超过64字符")
    private String cityName;

    /** 最低工作年限 */
    @Min(value = 0, message = "最低工作年限不能为负")
    private Integer minExperienceYears = 0;

    /** 学历要求编码 */
    private String educationRequirement = "NONE";

    /** 薪资信息（复用创建请求的嵌套结构） */
    @Valid
    @NotNull(message = "薪资信息不能为空")
    private JobCreateRequest.SalaryDTO salary;

    /** 岗位总HC */
    @Min(value = 1, message = "总HC至少为1")
    private Integer totalHc = 1;

    /** JD原文 */
    @Size(max = 20000, message = "JD原文不能超过20000字符")
    private String jdText;

    /** 岗位到期时间（P1可选，若提供须晚于当前时间） */
    @Future(message = "岗位到期时间必须晚于当前时间")
    private LocalDateTime expiresAt;

    /** 岗位乐观锁版本 */
    @NotNull(message = "岗位版本不能为空")
    private Integer version;

    /** 画像是否已确认（三件套之一，可整体缺失） */
    private Boolean profileConfirmed;

    /** 岗位画像版本（三件套之一，可整体缺失） */
    private Integer profileVersion;

    /** 岗位画像（三件套之一，可整体缺失） */
    @Valid
    private JobCreateRequest.JobProfileDTO profile;
}

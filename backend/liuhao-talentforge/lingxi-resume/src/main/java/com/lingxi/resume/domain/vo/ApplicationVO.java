package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递列表项视图对象（C端）
 *
 * <p>jobTitle/companyName 等来自 JOIN job_post / hr_company，非表列。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class ApplicationVO {

    /** 投递记录ID（雪花ID字符串序列化，防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称（JOIN job_post） */
    private String jobTitle;

    /** 城市展示名（JOIN job_post） */
    private String cityName;

    /** 企业名称（JOIN hr_company） */
    private String companyName;

    /** 企业Logo（JOIN hr_company） */
    private String companyLogo;

    /** 投递状态 */
    private String status;

    /** 投递时间 */
    private LocalDateTime submittedAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

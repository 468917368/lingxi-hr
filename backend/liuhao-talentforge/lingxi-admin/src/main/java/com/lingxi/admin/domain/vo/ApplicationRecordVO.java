package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 投递记录VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class ApplicationRecordVO {

    /** 投递ID */
    private Long id;

    /** 岗位名称 */
    private String jobTitle;

    /** 企业名称 */
    private String companyName;

    /** 状态 */
    private String status;

    /** 投递时间 */
    private LocalDateTime appliedAt;

    /** 最后更新时间 */
    private LocalDateTime lastUpdatedAt;
}

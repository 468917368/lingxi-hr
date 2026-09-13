package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位详情VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class JobDetailVO {

    /** 岗位ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 企业名称 */
    private String companyName;

    /** 岗位名称 */
    private String title;

    /** 城市 */
    private String city;

    /** 薪资范围 */
    private String salaryRange;

    /** 经验要求 */
    private String experience;

    /** 学历要求 */
    private String education;

    /** 状态 */
    private String status;

    /** HC详情 */
    private HeadcountVO headcount;

    /** 投递数 */
    private Integer applyCount;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 技能标签 */
    private List<String> skills;

    /** JD摘要 */
    private String jdSummary;
}

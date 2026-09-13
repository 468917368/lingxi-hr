package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 企业排行VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CompanyRankVO {

    /** 企业ID */
    private Long id;

    /** 企业名称 */
    private String name;

    /** HR对话次数 */
    private Integer conversationCount;
}

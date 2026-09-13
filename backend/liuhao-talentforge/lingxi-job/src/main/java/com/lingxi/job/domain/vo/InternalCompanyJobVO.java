package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 成员 A 服务间企业岗位列表响应项
 * <p>轻量只读 VO：仅供内部调用方按企业筛选/展示岗位，不泄露薪资、技能、画像等端侧结构。</p>
 *
 * @author lingxi-team
 * @since 2026-08-05
 */
@Data
public class InternalCompanyJobVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID（job_post.id） */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 岗位状态：DRAFT/PUBLISHED/PAUSED/CLOSED（调用方按业务自行筛选） */
    private String status;

    /** 可用 HC（total_hc - reserved_hc - confirmed_hc），仅供调用方判断容量，不代表可投递性 */
    private Integer availableHc;
}

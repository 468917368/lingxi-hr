package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试记录表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrInterview implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 面试记录ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 投递记录ID */
    private Long applicationId;

    /** 面试官用户ID */
    private Long interviewerId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 岗位ID */
    private Long jobId;

    /** 预约面试时间 */
    private LocalDateTime scheduledAt;

    /** 面试方式：OFFLINE/ONLINE/PHONE */
    private String method;

    /** 面试地点或视频链接 */
    private String location;

    /** 面试备注 */
    private String remark;

    /** 给候选人的留言 */
    private String candidateNote;

    /** 面试状态：PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

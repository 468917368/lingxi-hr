package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试列表项（系分文档 5.5.3）
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Data
public class InterviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 面试记录ID */
    private Long interviewId;

    /** 投递记录ID */
    private Long applicationId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 候选人姓名（来自 lingxi-user，降级为"用户"+id） */
    private String candidateName;

    /** 候选人头像 */
    private String candidateAvatar;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称（Feign 取投递快照） */
    private String jobTitle;

    /** 面试官用户ID */
    private Long interviewerId;

    /** 面试官姓名 */
    private String interviewerName;

    /** 预约面试时间 */
    private LocalDateTime scheduledAt;

    /** 面试方式：OFFLINE/ONLINE/PHONE */
    private String method;

    /** 面试方式说明 */
    private String methodDesc;

    /** 面试地点或视频链接 */
    private String location;

    /** 面试备注（HR 内部） */
    private String remark;

    /** 给候选人的留言 */
    private String candidateNote;

    /** 面试状态：PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String status;

    /** 面试状态说明 */
    private String statusDesc;

    /** 是否已生成 AI 题目（本期恒 false，出题在岗位侧 Interview Agent） */
    private Boolean hasQuestions;

    /** 是否已录入评估（草稿也算已录入） */
    private Boolean hasEvaluation;
}

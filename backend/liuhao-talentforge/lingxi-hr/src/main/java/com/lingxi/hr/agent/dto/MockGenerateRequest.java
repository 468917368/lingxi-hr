package com.lingxi.hr.agent.dto;

import lombok.Data;

/**
 * 生成题目请求
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockGenerateRequest {

    /** 目标岗位ID */
    private Long jobId;

    /** 目标岗位名称（冗余，岗位接口失败时兜底） */
    private String jobTitle;

    /** 题目数量：5/8/10，默认 5 */
    private Integer questionCount = 5;

    /** 简历ID（选填，不传取默认简历） */
    private Long resumeId;
}

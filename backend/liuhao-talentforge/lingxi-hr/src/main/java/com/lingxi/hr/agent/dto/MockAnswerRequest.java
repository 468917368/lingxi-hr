package com.lingxi.hr.agent.dto;

import lombok.Data;

/**
 * 提交答案请求
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockAnswerRequest {

    /** 题号（从1开始） */
    private Integer questionNumber;

    /** 候选人作答内容 */
    private String answer;
}

package com.lingxi.hr.agent.dto;

import lombok.Data;

/**
 * 面试题目 VO
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockQuestionVO {

    /** 题号（从1开始） */
    private Integer questionNumber;

    /** 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE */
    private String questionType;

    /** 考察维度 */
    private String dimension;

    /** 难度：EASY/MEDIUM/HARD */
    private String difficulty;

    /** 题目内容 */
    private String content;
}

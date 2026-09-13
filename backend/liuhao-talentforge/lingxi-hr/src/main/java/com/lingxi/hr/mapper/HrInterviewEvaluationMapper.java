package com.lingxi.hr.mapper;

import com.lingxi.hr.domain.entity.HrInterviewEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 面试评估 Mapper（Day 4-5）
 *
 * <p>与面试记录一对一（uk_interview_id）。草稿转正式：已有 is_draft=1 记录走
 * {@link #updateById} 覆盖（4104 仅对 is_draft=0 生效）。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Mapper
public interface HrInterviewEvaluationMapper {

    /**
     * 插入评估（自增主键回填 id）
     */
    int insert(HrInterviewEvaluation evaluation);

    /**
     * 按ID动态更新（草稿覆盖转正式）
     */
    int updateById(HrInterviewEvaluation evaluation);

    /**
     * 按面试ID查询评估（含草稿）
     */
    HrInterviewEvaluation selectByInterviewId(@Param("interviewId") Long interviewId);

    /**
     * 批量查询评估（列表 hasEvaluation 用）
     */
    List<HrInterviewEvaluation> selectByInterviewIds(@Param("interviewIds") List<Long> interviewIds);
}

package com.lingxi.resume.mapper;

import com.lingxi.resume.domain.entity.ResumeAbilityModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 能力模型Mapper
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Mapper
public interface ResumeAbilityModelMapper {

    /**
     * 根据简历ID查询（resume_id 唯一，1:1）
     */
    ResumeAbilityModel selectByResumeId(@Param("resumeId") Long resumeId);

    /**
     * 根据候选人ID查询列表
     */
    ResumeAbilityModel selectByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * 插入记录
     */
    int insert(ResumeAbilityModel abilityModel);

    /**
     * 按简历ID覆盖更新（重新解析时用）
     */
    int updateByResumeId(ResumeAbilityModel abilityModel);
}

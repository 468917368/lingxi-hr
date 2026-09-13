package com.lingxi.resume.mapper;

import com.lingxi.resume.domain.entity.ResumeDiagnosisReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 诊断报告Mapper
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Mapper
public interface ResumeDiagnosisReportMapper {

    /**
     * 根据ID查询报告详情
     */
    ResumeDiagnosisReport selectById(@Param("id") Long id);

    /**
     * 查询简历的诊断历史（同 resume+career 允许多条，按时间倒序，limit 控制返回条数）
     */
    List<ResumeDiagnosisReport> selectByResumeIdAndCareer(@Param("resumeId") Long resumeId,
                                                          @Param("career") String career,
                                                          @Param("limit") int limit);

    /**
     * 查询超出保留条数（keep）的最旧记录（用于历史裁剪）
     */
    List<ResumeDiagnosisReport> selectOverLimit(@Param("resumeId") Long resumeId,
                                                @Param("career") String career,
                                                @Param("keep") int keep);

    /**
     * 按 ID 批量删除
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 插入记录
     */
    int insert(ResumeDiagnosisReport report);
}

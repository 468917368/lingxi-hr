package com.lingxi.resume.mapper;

import com.lingxi.resume.domain.dto.ResumeQuery;
import com.lingxi.resume.domain.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历Mapper
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Mapper
public interface ResumeMapper {

    /**
     * 根据ID查询（未删除）
     */
    Resume selectById(@Param("id") Long id);

    /**
     * 根据候选人ID查询列表（未删除，按更新时间倒序）
     */
    List<Resume> selectByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * 条件查询（candidateId 必传，parseStatus/isDefault 可选）
     */
    List<Resume> selectByCondition(ResumeQuery query);

    /**
     * 统计候选人简历数量（5份上限，仅未删除）
     */
    int countByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * 查询候选人默认简历（投递时未指定 resumeId 使用；未删除）
     */
    Resume selectDefaultByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * 插入记录
     */
    int insert(Resume resume);

    /**
     * 动态更新（仅非空字段）
     */
    int updateById(Resume resume);

    /**
     * 取消候选人所有默认简历
     */
    int clearDefault(@Param("candidateId") Long candidateId);

    /**
     * 逻辑删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 条件状态流转（防并发：仅当当前状态为 fromStatus 时才更新为 toStatus）
     *
     * @return 影响行数（0=状态已被其他流程变更，跳过后续解析）
     */
    int updateParseStatus(@Param("id") Long id,
                          @Param("fromStatus") String fromStatus,
                          @Param("toStatus") String toStatus);

    /**
     * 查询中断/超时的解析记录（兜底扫描用）
     *
     * @param staleBefore 更新时间早于该时刻的记录（PENDING/PARSING 视为中断）
     */
    List<Resume> selectStaleForParse(@Param("staleBefore") LocalDateTime staleBefore);
}

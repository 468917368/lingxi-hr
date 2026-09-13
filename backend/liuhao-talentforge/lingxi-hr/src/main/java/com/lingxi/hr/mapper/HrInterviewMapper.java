package com.lingxi.hr.mapper;

import com.lingxi.hr.domain.entity.HrInterview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试记录 Mapper（Day 4-5）
 *
 * <p>分页查询：公司隔离 + 条件过滤（status/dateRange/jobId/interviewerId）+ LIMIT 分页。
 * 状态变更统一乐观锁 {@link #updateStatus}（rows=0 → 4101）。
 * 待评估列表 {@link #selectPendingEvaluations}：IN_PROGRESS 且无正式评估（草稿不算正式）。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Mapper
public interface HrInterviewMapper {

    /**
     * 插入面试记录（自增主键回填 id）
     */
    int insert(HrInterview interview);

    /**
     * 按ID查询
     */
    HrInterview selectById(@Param("id") Long id);

    /**
     * 分页计数（条件与 selectPage 一致）
     */
    long countPage(@Param("companyId") Long companyId,
                   @Param("status") String status,
                   @Param("dateRange") String dateRange,
                   @Param("jobId") Long jobId,
                   @Param("interviewerId") Long interviewerId,
                   @Param("method") String method,
                   @Param("keyword") String keyword);

    /**
     * 条件分页查询（按 scheduled_at DESC）
     */
    List<HrInterview> selectPage(@Param("companyId") Long companyId,
                                 @Param("status") String status,
                                 @Param("dateRange") String dateRange,
                                 @Param("jobId") Long jobId,
                                 @Param("interviewerId") Long interviewerId,
                                 @Param("method") String method,
                                 @Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /**
     * 乐观锁更新状态：fromStatus → toStatus，返回影响行数（0 表示状态已被并发变更）
     */
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    /**
     * 同面试官时间冲突检测（非终态，scheduled_at 落在 [start, end] 区间）
     */
    int countByInterviewerInTimeRange(@Param("interviewerId") Long interviewerId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * 待评估列表：IN_PROGRESS 且无正式评估（LEFT JOIN is_draft=0）
     *
     * @param interviewerId 面试官过滤（面试官仅查本人；管理员传 null 查全企业）
     */
    List<HrInterview> selectPendingEvaluations(@Param("companyId") Long companyId,
                                               @Param("interviewerId") Long interviewerId);

    /**
     * 查某面试官负责的投递ID集合（候选人列表范围限定，去重）
     */
    List<Long> selectApplicationIdsByInterviewer(@Param("companyId") Long companyId,
                                                 @Param("interviewerId") Long interviewerId);
}

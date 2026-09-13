package com.lingxi.resume.mapper;

import com.lingxi.resume.domain.dto.ApplicationQuery;
import com.lingxi.resume.domain.entity.ResumeApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 投递记录Mapper
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Mapper
public interface ResumeApplicationMapper {

    /**
     * 根据ID查询投递详情
     */
    ResumeApplication selectById(@Param("id") Long id);

    /**
     * 根据候选人ID查询投递列表（按投递时间倒序）
     */
    List<ResumeApplication> selectByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * C端投递列表条件查询（JOIN job_post/hr_company，candidateId 必传）
     */
    List<ResumeApplication> selectByCondition(ApplicationQuery query);

    /**
     * HR端候选人列表条件查询（JOIN job_post/sys_user，companyId 必传，status/jobId 可选）
     */
    List<ResumeApplication> selectByCompanyId(ApplicationQuery query);

    /**
     * 根据ID查询投递详情（JOIN job_post/hr_company 获取岗位与企业信息）
     */
    ResumeApplication selectByIdWithJob(@Param("id") Long id);

    /**
     * 查询岗位+候选人的投递记录（防重复投递校验）
     */
    ResumeApplication selectByJobAndCandidate(@Param("jobId") Long jobId, @Param("candidateId") Long candidateId);

    /**
     * 校验岗位可投递性（单库直查 job_post：存在 + 未删除 + PUBLISHED）
     *
     * @return 1=可投递，0=不存在/已删除/非发布中
     */
    int countOpenJob(@Param("jobId") Long jobId);

    /**
     * 插入记录（初始状态 SUBMITTED）
     */
    int insert(ResumeApplication application);

    /**
     * 重新投递：将终态记录重置为 SUBMITTED（撤回/淘汰/拒Offer 后重新投递）
     */
    int reapply(@Param("id") Long id, @Param("resumeId") Long resumeId,
                @Param("matchScore") BigDecimal matchScore);

    /**
     * 条件更新状态（状态机核心）：仅当当前状态等于 fromStatus 时更新为 toStatus，并写入对应状态时间戳
     * rows=0 表示状态已被其他操作变更（天然幂等）
     *
     * @param target 状态时间戳列名白名单：viewed_at/screened_at/interviewing_at/offerable_at/offered_at/rejected_at/withdrawn_at；
     *               可为 null/空（无独立时间戳列的状态，仅更新 status/updated_at）
     */
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("target") String target);

    /**
     * 动态更新（matchScore/rejectFeedback 等，仅非空字段）
     */
    int updateById(ResumeApplication application);

    /**
     * 统计平台投递总数（管理后台看板）
     */
    long countAll();

    /**
     * 统计近 N 天每日投递数（管理后台趋势图）
     *
     * @param days 天数（含今日）
     * @return 每行 {dt: LocalDate, cnt: Long}，按日期升序
     */
    List<Map<String, Object>> countByDay(@Param("days") int days);

    /**
     * 查询用户已投递的岗位ID列表
     *
     * @param candidateId 候选人用户ID
     * @return 已投递的岗位ID列表
     */
    List<Long> selectAppliedJobIds(@Param("candidateId") Long candidateId);
}

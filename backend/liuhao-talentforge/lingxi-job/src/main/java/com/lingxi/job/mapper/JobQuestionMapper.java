package com.lingxi.job.mapper;

import com.lingxi.job.domain.dto.query.QuestionListQuery;
import com.lingxi.job.domain.dto.query.QuestionSearchQuery;
import com.lingxi.job.domain.entity.JobQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业私有题库表 Mapper
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Mapper
public interface JobQuestionMapper {

    /**
     * 按ID查询题目（未删除）
     */
    JobQuestion selectById(@Param("id") Long id);

    /**
     * Agent 题库搜索（百宝箱 Tool3）
     * <p>
     * 查询条件：companyId（必传）、jobType、skillTags、questionType、difficulty。
     * 排序回归系分 3.2.4：
     * ①技能标签重合度 DESC → ②difficulty 精确匹配优先 → ③每题型取一题 → ④id ASC 稳定。
     * 每种题型最多 1 条、合计 ≤4 条。
     * </p>
     *
     * @param q 查询条件
     * @return 题目列表（每题型至多 1 条）
     */
    List<JobQuestion> selectForAgentSearch(@Param("q") QuestionSearchQuery q);

    /**
     * 出题确定性选题专用（企业私有题确定性选题）
     * <p>
     * 与 {@link #selectForAgentSearch} 区别：内层 WHERE 固定 {@code question_type IN (4 标准题型)}
     * （非标准题型不抢占 LIMIT 4）；删 difficulty WHERE 过滤（difficulty 改走 ORDER BY CASE 排序优先）。
     * selectForAgentSearch 保持 Tool3 语义原样。
     * </p>
     *
     * @param q 查询条件（companyId 必传、jobType 可选、difficulty 必传、skillTags 可选）
     * @return 题目列表（仅标准 4 题型，每题型至多 1 条，合计 ≤4 条）
     */
    List<JobQuestion> selectForPrivateQuestion(@Param("q") QuestionSearchQuery q);

    // ==================== 题库写侧（阶段6.1） ====================

    /**
     * 企业隔离单查（id + companyId，未删除）
     */
    JobQuestion selectByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * HR 题库分页列表（SQL 固定 company_id，四筛 jobType/questionType/difficulty/status，按 id DESC）
     */
    List<JobQuestion> selectHrQuestionList(@Param("companyId") Long companyId,
                                           @Param("q") QuestionListQuery q);

    /**
     * 未删除同 content 查重（excludeId 编辑时排除自身）
     */
    int countActiveByCompanyAndContentHash(@Param("companyId") Long companyId,
                                           @Param("contentSha256") String contentSha256,
                                           @Param("excludeId") Long excludeId);

    /**
     * 查找可复用的软删行（同 content，deleted_at 非空，最多一条）
     */
    JobQuestion selectDeletedByContentHash(@Param("companyId") Long companyId,
                                           @Param("contentSha256") String contentSha256);

    /**
     * 新增题目（useGeneratedKeys 回填 id）
     */
    int insert(JobQuestion question);

    /**
     * 编辑题目（乐观锁 version，WHERE id+company_id+deleted_at IS NULL）
     */
    int updateByIdAndCompanyIdAndVersion(JobQuestion question);

    /**
     * 软删行复用恢复：WHERE id+company_id+deleted_at IS NOT NULL（唯一不以 deleted_at IS NULL 守卫的写 SQL）
     */
    int restoreDeleted(@Param("id") Long id, @Param("companyId") Long companyId,
                       @Param("question") JobQuestion question);

    /**
     * 软删除题目（乐观锁 version）
     */
    int softDelete(@Param("id") Long id, @Param("companyId") Long companyId,
                   @Param("version") Integer version);

    /**
     * 启用题目（INACTIVE→ACTIVE，乐观锁）
     */
    int enable(@Param("id") Long id, @Param("companyId") Long companyId,
               @Param("version") Integer version);

    /**
     * 停用题目（ACTIVE→INACTIVE，乐观锁）
     */
    int disable(@Param("id") Long id, @Param("companyId") Long companyId,
                @Param("version") Integer version);

    /**
     * 审核流转（PENDING_REVIEW→ACTIVE/REJECTED，落 reviewed 三字段，乐观锁）
     */
    int review(@Param("id") Long id, @Param("companyId") Long companyId,
               @Param("version") Integer version, @Param("status") String status,
               @Param("reviewedBy") Long reviewedBy, @Param("reviewedAt") java.time.LocalDateTime reviewedAt,
               @Param("reviewReason") String reviewReason);

    /**
     * 待审核计数（companyId + status=PENDING_REVIEW + 未删除）
     */
    long countPending(@Param("companyId") Long companyId);
}

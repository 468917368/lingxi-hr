package com.lingxi.job.mapper;

import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.vo.AdminJobListVO;
import com.lingxi.job.domain.vo.HrJobListVO;
import com.lingxi.job.domain.vo.InternalCompanyJobVO;
import com.lingxi.job.domain.vo.InternalJobCardVO;
import com.lingxi.job.domain.vo.JobCardVO;
import com.lingxi.job.domain.vo.JobTypeDistributionVO;
import com.lingxi.job.domain.entity.JobPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 岗位表 Mapper
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Mapper
public interface JobPostMapper {

    /**
     * 按ID查询岗位（未删除）
     */
    JobPost selectById(@Param("id") Long id);

    /**
     * 按ID+企业ID查询岗位（企业隔离，HR 端使用）
     */
    JobPost selectByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * 按ID查询岗位并加锁（HC 操作使用）
     */
    JobPost selectByIdForUpdate(@Param("id") Long id);

    /**
     * 内部岗位搜索（仅 PUBLISHED，JOIN job_profile 取画像信息，PageHelper 分页）
     */
    List<InternalJobCardVO> selectForInternalSearch(@Param("q") Map<String, Object> query);

    /**
     * 查询企业全部未删除岗位（服务间轻量列表，含各业务状态，稳定排序）
     */
    List<InternalCompanyJobVO> selectByCompanyId(@Param("companyId") Long companyId);

    /**
     * 岗位总数（排除已删除）
     */
    long countAll();

    /**
     * 今日新增岗位数
     */
    long countToday();

    /**
     * 岗位类型分布统计
     */
    List<JobTypeDistributionVO> selectJobTypeDistribution();

    /**
     * HC 计数 + 状态 + 原因 + 时间一次 UPDATE（乐观锁 version；HC 状态自动管理唯一写入口）
     * <p>一次原子更新 reserved/confirmed/status/pause_reason/close_reason/closed_at/published_at，
     * 绝不在 updateHcCounts 后再单独改状态（version 自增两次会自我乐观锁冲突）。</p>
     */
    int updateHcAndStatus(JobPost post);

    /**
     * 管理员分页查询（JOIN job_profile，PageHelper 分页）
     */
    List<AdminJobListVO> selectAdminList(@Param("q") Map<String, Object> query);

    /**
     * 插入岗位（创建草稿，useGeneratedKeys 回填 id）
     */
    int insert(JobPost post);

    /**
     * 按ID+企业ID乐观锁更新岗位基础信息（WHERE 含 company_id/version/deleted_at）
     */
    int updateByIdAndVersion(JobPost post);

    /**
     * 软删除草稿岗位（仅 DRAFT，乐观锁 version）
     */
    int softDelete(@Param("id") Long id, @Param("companyId") Long companyId, @Param("version") Integer version);

    /**
     * 发布岗位（DRAFT→PUBLISHED，乐观锁 version）
     */
    int publish(JobPost post);

    /**
     * 关闭岗位（PUBLISHED/PAUSED→CLOSED，closeReason=MANUAL，乐观锁 version）
     */
    int close(JobPost post);

    /**
     * 重新开放岗位（CLOSED→PUBLISHED，清空 closeReason/closedAt，乐观锁 version）
     */
    int reopen(JobPost post);

    /**
     * 违规下架岗位（PUBLISHED/PAUSED→CLOSED，closeReason=VIOLATION，乐观锁 version）
     * <p>幂等由 Service 前置判断（CLOSED+VIOLATION 短路返回），本方法仅在首次迁移时调用。</p>
     */
    int offline(JobPost post);

    /**
     * HR 岗位分页列表（companyId 强制过滤，JOIN job_profile，PageHelper 分页）
     */
    List<HrJobListVO> selectHrList(@Param("q") Map<String, Object> query);

    /**
     * C端岗位搜索（仅 PUBLISHED，JOIN job_profile 取画像，动态筛选+推荐分+排序，PageHelper 分页）
     */
    List<JobCardVO> selectForCandidateSearch(@Param("q") Map<String, Object> query);

    /**
     * C端最新排序轻量搜索（阶段6.3）：复用全部筛选条件，但不计算推荐分（recommendScore 恒 0）
     * <p>供 LATEST 排序与无筛选 RECOMMENDED 降级路径使用，避免为这两类请求计算推荐分 CASE。</p>
     */
    List<JobCardVO> selectLatestForCandidateSearch(@Param("q") Map<String, Object> query);

    /**
     * 按ID查询已发布岗位（C端详情，仅 PUBLISHED 且未删除）
     */
    JobPost selectPublishedById(@Param("id") Long id);

    /**
     * 已发布岗位的城市去重列表（C端搜索选项，避免前端硬编码城市）
     */
    List<JobOptionsResponse.CityOption> selectCityOptions();

    /**
     * 已发布岗位画像核心技能去重列表（C端搜索选项，JSON_TABLE 提取 name，去空去重排序，LIMIT 控制数量）
     */
    List<String> selectSkillOptions(@Param("limit") int limit);

    /**
     * 扫描已过期且仍开放的岗位（PUBLISHED/PAUSED），供定时任务逐条幂等关闭
     */
    List<JobPost> listExpiredOpen();

    /**
     * 原子关闭单个过期岗位（status=#{fromStatus} 断言保证审计 fromStatus 与 DB 一致）；
     * rows=1 成功、0=状态并发变化/已关闭（本轮跳过，下轮重扫）
     */
    int closeExpiredById(@Param("id") Long id, @Param("fromStatus") String fromStatus);
}

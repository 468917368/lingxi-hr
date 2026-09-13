package com.lingxi.job.mapper;

import com.lingxi.job.domain.entity.JobFavorite;
import com.lingxi.job.domain.vo.FavoriteJobVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位收藏表 Mapper
 * <p>所有方法 WHERE 均含 candidate_id（候选人隔离强制）；uk_candidate_job 唯一索引作为数据库级幂等兜底。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Mapper
public interface JobFavoriteMapper {

    /**
     * 按候选人+岗位查收藏（幂等判断）
     */
    JobFavorite selectByCandidateAndJob(@Param("candidateId") Long candidateId, @Param("jobId") Long jobId);

    /**
     * 新增收藏（uk_candidate_job 唯一键幂等兜底）
     */
    int insert(JobFavorite favorite);

    /**
     * 按候选人+岗位物理删除（取消收藏，幂等）
     */
    int deleteByCandidateAndJob(@Param("candidateId") Long candidateId, @Param("jobId") Long jobId);

    /**
     * 候选人收藏分页列表（JOIN job_post，按 f.created_at DESC, f.id DESC 稳定排序，PageHelper 分页不写 LIMIT）
     */
    List<FavoriteJobVO> selectFavoriteList(@Param("candidateId") Long candidateId);

    /**
     * 候选人收藏的岗位 ID 集合（空集合返回 []）
     */
    List<Long> selectJobIdsByCandidateId(@Param("candidateId") Long candidateId);
}

package com.lingxi.job.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.job.domain.dto.response.FavoriteResponse;
import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.vo.FavoriteJobVO;
import com.lingxi.job.domain.vo.JobCardVO;
import com.lingxi.job.domain.vo.JobDetailVO;

import java.util.List;
import java.util.Map;

/**
 * C端岗位发现服务
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
public interface CandidateJobService {

    /**
     * C端岗位搜索（仅 PUBLISHED，多条件筛选 + 推荐分/排序 + 分页）
     */
    PageResult<JobCardVO> searchJobs(Map<String, Object> query);

    /**
     * C端岗位详情（仅 PUBLISHED，含脱敏画像）
     */
    JobDetailVO getJobDetail(Long jobId);

    /**
     * 已发布岗位的城市去重列表（搜索选项，数据驱动避免前端硬编码）
     */
    List<JobOptionsResponse.CityOption> listCityOptions();

    /**
     * 已发布岗位画像核心技能去重列表（搜索选项，数据驱动避免前端硬编码）
     */
    List<String> listSkillOptions();

    /**
     * 收藏/取消收藏岗位（仅 CANDIDATE，幂等）
     * <p>favorited=true 收藏（仅 PUBLISHED 且未删除岗位，重复收藏幂等成功，不抛 2106）；
     * favorited=false 取消收藏（不校验岗位当前状态，未收藏幂等成功）。</p>
     * <p>返回裸 FavoriteResponse（Result 是 HTTP 层概念，由 Controller 统一 Result.success 包装）。</p>
     */
    FavoriteResponse favoriteJob(Long jobId, Boolean favorited);

    /**
     * 我的收藏分页列表（favoritedAt DESC, id DESC 稳定排序）
     * <p>已关闭/已删除/物理缺失岗位仍展示并标记 isOffline/deleted，收藏记录不丢。</p>
     */
    PageResult<FavoriteJobVO> listFavorites(int page, int size);

    /**
     * 当前候选人收藏的岗位 ID 集合（搜索/详情页收藏按钮回显用）
     * <p>candidateId 从 UserContext.getUserId() 取，禁止外部传 ID 防误传。</p>
     */
    List<Long> listFavoriteJobIds();
}

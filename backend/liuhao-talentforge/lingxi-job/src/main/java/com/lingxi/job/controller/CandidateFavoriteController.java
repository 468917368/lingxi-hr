package com.lingxi.job.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.vo.FavoriteJobVO;
import com.lingxi.job.service.CandidateJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端我的收藏接口（阶段6.2）
 * <p>路径采用网关已放行的 /api/v1/favorites/**（gateway 路由已配置，不依赖网关团队）；
 * 类级 @RequireLogin 兜底未登录，方法级 @RequireRole("CANDIDATE") 限定候选人。
 * candidateId 一律从 UserContext 取，不信任前端传入。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@RequireLogin
public class CandidateFavoriteController {

    private final CandidateJobService candidateJobService;

    /**
     * 我的收藏分页列表（favoritedAt DESC, id DESC 稳定排序）
     * <p>已关闭/已删除/物理缺失岗位仍展示并标记 isOffline/deleted，收藏记录不丢。</p>
     */
    @GetMapping
    @RequireRole("CANDIDATE")
    public Result<PageResult<FavoriteJobVO>> listFavorites(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(candidateJobService.listFavorites(page, size));
    }

    /**
     * 收藏岗位 ID 集合（搜索/详情页收藏按钮回显，一次批量取回，避免 N+1）
     * <p>无入参，candidateId 由 Service 从 UserContext 取（防误传）。</p>
     */
    @GetMapping("/ids")
    @RequireRole("CANDIDATE")
    public Result<List<Long>> listFavoriteIds() {
        return Result.success(candidateJobService.listFavoriteJobIds());
    }
}

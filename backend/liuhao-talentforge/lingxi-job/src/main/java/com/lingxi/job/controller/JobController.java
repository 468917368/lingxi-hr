package com.lingxi.job.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.dto.request.FavoriteRequest;
import com.lingxi.job.domain.dto.response.FavoriteResponse;
import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.vo.JobCardVO;
import com.lingxi.job.domain.vo.JobDetailVO;
import com.lingxi.job.enums.IndustryCodeEnum;
import com.lingxi.job.enums.SortByEnum;
import com.lingxi.job.service.CandidateJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * C端岗位发现接口
 * <p>类级 @RequireLogin 兜底未登录（登录即可，不限角色），与网关 AuthFilter 对 /api/v1/jobs/** 强制 JWT 一致。
 * 发现类接口（搜索/选项/详情）对任意已登录角色开放；仅收藏 {@link #favoriteJob} 方法级限定 CANDIDATE。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Validated
@RequireLogin
public class JobController {

    private final CandidateJobService candidateJobService;

    /**
     * C端岗位搜索：13 个查询参数（10 个筛选 + sortBy + page + size）
     */
    @GetMapping
    public Result<PageResult<JobCardVO>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industryGroupCode,
            @RequestParam(required = false) String industryCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) List<String> skillTags,
            @RequestParam(required = false) Long salaryMin,
            @RequestParam(required = false) Long salaryMax,
            @RequestParam(required = false) Integer experienceMax,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String jobType,
            @RequestParam(defaultValue = "RECOMMENDED") String sortBy,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> query = new HashMap<>();
        query.put("keyword", keyword);
        query.put("industryGroupCode", industryGroupCode);
        query.put("industryCode", industryCode);
        query.put("cityCode", cityCode);
        query.put("skillTags", skillTags);
        query.put("salaryMin", salaryMin);
        query.put("salaryMax", salaryMax);
        query.put("experienceMax", experienceMax);
        query.put("education", education);
        query.put("jobType", jobType);
        query.put("sortBy", sortBy);
        query.put("page", page);
        query.put("size", size);
        return Result.success(candidateJobService.searchJobs(query));
    }

    /**
     * C端岗位搜索静态选项（10 行业 + 3 排序 + 已发布岗位城市/技能去重）
     * <p>行业/排序来自静态枚举；城市/技能读库去重（selectCityOptions/selectSkillOptions），保证选项与真实岗位一致、前端无需硬编码。
     * 仅主数据，无企业私有信息，对任意已登录角色开放（HR 创建/编辑岗位行业下拉亦复用本接口）。</p>
     */
    @GetMapping("/options")
    public Result<JobOptionsResponse> getJobOptions() {
        JobOptionsResponse resp = new JobOptionsResponse();
        resp.setIndustries(Arrays.stream(IndustryCodeEnum.values())
                .map(e -> new JobOptionsResponse.IndustryOption(e.getCode(), e.getCode(), e.getDesc()))
                .collect(Collectors.toList()));
        resp.setSortOptions(Arrays.stream(SortByEnum.values())
                .map(e -> new JobOptionsResponse.SortOption(e.getCode(), e.getDesc()))
                .collect(Collectors.toList()));
        resp.setCities(candidateJobService.listCityOptions());
        resp.setSkills(candidateJobService.listSkillOptions());
        return Result.success(resp);
    }

    /**
     * C端岗位详情（仅 PUBLISHED，含脱敏画像）
     * <p>路由无正则：非数字 ID 触发类型不匹配 → HTTP 400；Spring MVC 优先匹配静态 /options 不被捕获。</p>
     */
    @GetMapping("/{jobId}")
    public Result<JobDetailVO> getJobDetail(@PathVariable Long jobId) {
        return Result.success(candidateJobService.getJobDetail(jobId));
    }

    /**
     * 收藏/取消收藏岗位（仅 CANDIDATE，幂等）
     * <p>favorited=true 收藏（仅 PUBLISHED 未删除岗位，重复收藏幂等成功）；false 取消收藏（岗位已关闭/删除也可取消，未收藏幂等成功）。
     * userId 从 UserContext 取，不信任前端传入。Service 返回裸 FavoriteResponse，统一 Result.success 包装。</p>
     */
    @PostMapping("/{jobId:[0-9]+}/favorite")
    @RequireRole("CANDIDATE")
    public Result<FavoriteResponse> favoriteJob(@PathVariable Long jobId,
                                                @RequestBody @Valid FavoriteRequest request) {
        return Result.success(candidateJobService.favoriteJob(jobId, request.getFavorited()));
    }
}

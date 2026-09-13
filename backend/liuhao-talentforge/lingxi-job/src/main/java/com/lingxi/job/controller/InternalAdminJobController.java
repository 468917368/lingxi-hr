package com.lingxi.job.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.domain.dto.request.JobOfflineRequest;
import com.lingxi.job.domain.dto.response.JobStatusResponse;
import com.lingxi.job.domain.dto.response.JobTypeDistributionResponse;
import com.lingxi.job.domain.dto.response.JobStatisticsValueResponse;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.vo.AdminJobDetailVO;
import com.lingxi.job.domain.vo.AdminJobListVO;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.service.InternalAdminJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 内部管理员岗位接口控制器（成员 E 依赖）
 * <p>统计/分页/详情。企业端查询严格按 companyId 隔离；不返回 companyName/applyCount（由 E/C 补齐）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalAdminJobController {

    private final JobPostMapper jobPostMapper;
    private final JobProfileMapper jobProfileMapper;
    private final InternalAdminJobService internalAdminJobService;

    /**
     * 岗位总数（排除已删除）
     */
    @GetMapping("/internal/jobs/statistics/count")
    public Result<JobStatisticsValueResponse> countJobs() {
        JobStatisticsValueResponse resp = new JobStatisticsValueResponse();
        resp.setValue(jobPostMapper.countAll());
        return Result.success(resp);
    }

    /**
     * 今日新增岗位数
     */
    @GetMapping("/internal/jobs/statistics/today-count")
    public Result<JobStatisticsValueResponse> countTodayJobs() {
        JobStatisticsValueResponse resp = new JobStatisticsValueResponse();
        resp.setValue(jobPostMapper.countToday());
        return Result.success(resp);
    }

    /**
     * 岗位类型分布
     */
    @GetMapping("/internal/jobs/statistics/distribution")
    public Result<JobTypeDistributionResponse> getJobTypeDistribution() {
        JobTypeDistributionResponse resp = new JobTypeDistributionResponse();
        resp.setItems(jobPostMapper.selectJobTypeDistribution());
        return Result.success(resp);
    }

    /**
     * 管理员岗位分页（企业端：companyId 严格过滤）
     */
    @GetMapping("/internal/admin/jobs")
    public Result<PageResult<AdminJobListVO>> listAdminJobs(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> query = new HashMap<>();
        query.put("companyId", companyId);
        query.put("status", status);
        query.put("keyword", keyword);

        // 显式收敛分页边界：page>=1，size 1~50（不依赖 PageHelper）
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PageHelper.startPage(safePage, safeSize);
        List<AdminJobListVO> list = jobPostMapper.selectAdminList(query);
        PageInfo<AdminJobListVO> pageInfo = new PageInfo<>(list);

        // 解析 skillTagsRaw → skillTags，清空原始字段
        for (AdminJobListVO vo : list) {
            vo.setSkillTags(parseSkillTags(vo.getSkillTagsRaw()));
            vo.setSkillTagsRaw(null);
        }
        return Result.success(PageResult.of(list, pageInfo.getTotal(), safePage, safeSize));
    }

    /**
     * 管理员岗位详情（job_post + job_profile 组装，不返回 companyName/applyCount）
     */
    @GetMapping("/internal/admin/jobs/{jobId:[0-9]+}")
    public Result<AdminJobDetailVO> getAdminJobDetail(@PathVariable Long jobId) {
        JobPost post = jobPostMapper.selectById(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        JobProfile profile = jobProfileMapper.selectByJobId(jobId);

        AdminJobDetailVO vo = new AdminJobDetailVO();
        vo.setJobId(post.getId());
        vo.setCompanyId(post.getCompanyId());
        vo.setTitle(post.getTitle());
        vo.setStatus(post.getStatus());
        vo.setPauseReason(post.getPauseReason());
        vo.setCloseReason(post.getCloseReason());
        vo.setJdText(post.getJdText());
        vo.setJdSummary(post.getJdSummary());
        vo.setTotalHc(post.getTotalHc());
        vo.setReservedHc(post.getReservedHc());
        vo.setConfirmedHc(post.getConfirmedHc());
        vo.setAvailableHc(post.getTotalHc() - post.getReservedHc() - post.getConfirmedHc());
        vo.setVersion(post.getVersion());
        vo.setPublishedAt(post.getPublishedAt());
        vo.setClosedAt(post.getClosedAt());
        vo.setExpiresAt(post.getExpiresAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());

        if (profile != null) {
            vo.setJobType(profile.getJobType());
            vo.setCoreSkills(parseNameList(profile.getCoreSkills()));
            vo.setSoftSkills(parseNameList(profile.getSoftSkills()));
            vo.setIndustryExperience(profile.getIndustryExperience());
            vo.setInterviewFocus(parseInterviewFocus(profile.getInterviewFocus()));
            vo.setHiddenRequirements(profile.getHiddenRequirements());
            vo.setProfileConfirmed(StringUtils.hasText(profile.getJobType()));
        } else {
            vo.setProfileConfirmed(false);
        }
        return Result.success(vo);
    }

    /**
     * 管理员违规下架岗位（PUBLISHED/PAUSED→CLOSED，closeReason=VIOLATION，乐观锁）
     * <p>成员 E 从列表/详情取得最新 version 后调用；发生 2102 时重新查询详情取得新 version 由用户确认后重试。</p>
     */
    @PostMapping("/internal/admin/jobs/{jobId:[0-9]+}/offline")
    public Result<JobStatusResponse> offlineJob(@PathVariable Long jobId,
                                                @RequestBody @Valid JobOfflineRequest request,
                                                @RequestHeader(value = "X-Operator-Id") Long operatorId) {
        return Result.success("违规下架成功", internalAdminJobService.offlineJob(jobId, request, operatorId));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 解析 core_skills/soft_skills JSON → 名称列表
     */
    private List<String> parseNameList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> items = JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return items.stream()
                    .map(m -> m.get("name") == null ? null : String.valueOf(m.get("name")))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析画像 JSON 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 interview_focus JSON → 考察重点列表
     */
    private List<String> parseInterviewFocus(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtil.getMapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 interview_focus 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 core_skills JSON → 技能标签列表（adminList 用）
     */
    private List<String> parseSkillTags(String coreSkillsJson) {
        return parseNameList(coreSkillsJson);
    }
}

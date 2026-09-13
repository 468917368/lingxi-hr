package com.lingxi.job.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.dto.request.GenerateQuestionsRequest;
import com.lingxi.job.domain.dto.request.JdParseRequest;
import com.lingxi.job.domain.dto.request.JobCreateRequest;
import com.lingxi.job.domain.dto.request.JobStatusActionRequest;
import com.lingxi.job.domain.dto.request.JobUpdateRequest;
import com.lingxi.job.domain.dto.response.JdParseResponse;
import com.lingxi.job.domain.dto.response.JobCreateResponse;
import com.lingxi.job.domain.dto.response.JobOptionsResponse;
import com.lingxi.job.domain.dto.response.JobStatusResponse;
import com.lingxi.job.domain.dto.response.JobUpdateResponse;
import com.lingxi.job.domain.vo.HrJobDetailVO;
import com.lingxi.job.domain.vo.HrJobListVO;
import com.lingxi.job.domain.vo.JobStatusLogVO;
import com.lingxi.job.service.AiParseService;
import com.lingxi.job.service.HrJobService;
import com.lingxi.job.service.InterviewAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * B端 HR 岗位管理接口
 * <p>类级 @RequireLogin 兜底未登录，方法级 @RequireRole("HR") 限定企业 HR 角色。
 * companyId 一律从 UserContext 获取，不信任前端传入。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/hr/jobs")
@RequiredArgsConstructor
@Validated
@RequireLogin
@RequireRole({"HR"})
public class HrJobController {

    private final HrJobService hrJobService;
    private final AiParseService aiParseService;
    private final InterviewAgentService interviewAgentService;

    /**
     * JD 解析（F-09）：JD 文本 → 岗位画像草稿
     */
    @PostMapping("/jd/parse")
    @RequireRole("HR")
    public Result<JdParseResponse> parseJd(@RequestBody @Valid JdParseRequest request) {
        return Result.success(aiParseService.parseJd(request));
    }

    /**
     * Interview Agent SSE 出题（F-17）：progress* → result → done | error → close
     * <p>角色放宽给面试官（阶段4c）：本企业 HR/面试官均可对本企业该岗位的合法投递出题；
     * 企业隔离与投递白名单由 Service 层校验（见 InterviewAgentServiceImpl.validateContext）。</p>
     */
    @PostMapping(value = "/{jobId:[0-9]+}/interview-questions/generate",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireRole({"HR", "INTERVIEWER"})
    public SseEmitter generateQuestions(@PathVariable Long jobId,
                                        @RequestBody @Valid GenerateQuestionsRequest request) {
        return interviewAgentService.generateQuestions(jobId, request);
    }

    /**
     * HR 城市选项（全部启用城市，供创建/编辑岗位下拉；响应结构与 C 端 /jobs/options 统一）
     */
    @GetMapping("/options")
    @RequireRole("HR")
    public Result<JobOptionsResponse> listCityOptions() {
        JobOptionsResponse response = new JobOptionsResponse();
        response.setCities(hrJobService.listCityOptions());
        return Result.success(response);
    }

    /**
     * 创建岗位（DRAFT 草稿，job_post + job_profile 双表）
     */
    @PostMapping
    @RequireRole("HR")
    public Result<JobCreateResponse> createJob(@RequestBody @Valid JobCreateRequest request) {
        return Result.success("岗位创建成功", hrJobService.createJob(request));
    }

    /**
     * 编辑岗位（仅 DRAFT，双表乐观锁）
     */
    @PutMapping("/{jobId:[0-9]+}")
    @RequireRole("HR")
    public Result<JobUpdateResponse> updateJob(@PathVariable Long jobId,
                                               @RequestBody @Valid JobUpdateRequest request) {
        return Result.success("岗位编辑成功", hrJobService.updateJob(jobId, request));
    }

    /**
     * 删除草稿岗位（软删除，HTTP 200 + Result&lt;Void&gt; code=200）
     */
    @DeleteMapping("/{jobId:[0-9]+}")
    @RequireRole("HR")
    public Result<Void> deleteJob(@PathVariable Long jobId,
                                  @RequestParam(required = false) Integer version) {
        hrJobService.deleteJob(jobId, version);
        return Result.success("岗位删除成功", null);
    }

    /**
     * 岗位状态机变更（PUBLISH/CLOSE/REOPEN，乐观锁 version）
     */
    @PatchMapping("/{jobId:[0-9]+}/status")
    @RequireRole("HR")
    public Result<JobStatusResponse> changeJobStatus(@PathVariable Long jobId,
                                                     @RequestBody @Valid JobStatusActionRequest request) {
        return Result.success("状态变更成功", hrJobService.changeJobStatus(jobId, request));
    }

    /**
     * HR 岗位分页列表（本企业，companyId 强制从 UserContext 取）
     */
    @GetMapping
    @RequireRole("HR")
    public Result<PageResult<HrJobListVO>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> query = new HashMap<>();
        query.put("status", status);
        query.put("page", page);
        query.put("size", size);
        return Result.success(hrJobService.listJobs(query));
    }

    /**
     * 岗位状态变更历史（本企业，created_at DESC，分页）
     */
    @GetMapping("/{jobId:[0-9]+}/status-history")
    @RequireRole("HR")
    public Result<PageResult<JobStatusLogVO>> getStatusHistory(@PathVariable Long jobId,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(hrJobService.getStatusHistory(jobId, page, size));
    }

    /**
     * HR 岗位详情（完整字段 + 画像，企业隔离）
     */
    @GetMapping("/{jobId:[0-9]+}")
    @RequireRole("HR")
    public Result<HrJobDetailVO> getJobDetail(@PathVariable Long jobId) {
        return Result.success(hrJobService.getHrJobDetail(jobId));
    }
}

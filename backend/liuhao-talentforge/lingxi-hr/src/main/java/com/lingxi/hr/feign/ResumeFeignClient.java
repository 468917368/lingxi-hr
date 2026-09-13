package com.lingxi.hr.feign;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.dto.ApplicationDTO;
import com.lingxi.hr.feign.dto.ResumeDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * lingxi-resume Feign 客户端
 *
 * <p>契约与成员C确认（2026-08-03）：列表返回 {@link PageResult}（透传 total），
 * 筛选/排序在数据源（C 侧）完成；状态更新支持可选 {@code rejectFeedback}（落选反馈）。
 *
 * @author 成员D
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-resume", path = "/internal")
public interface ResumeFeignClient {

    @GetMapping("/applications/{id}")
    Result<ApplicationDTO> getApplication(@PathVariable("id") Long id);

    /**
     * 简历详情（内部接口，免归属校验，仅供 lingxi-hr 调用）
     *
     * <p>契约与成员C确认（2026-08-06）：全量简历已挪至 {@code GET /internal/resumes/{id}/detail}
     * （{@link ResumeDetailDTO}，含 facePhotoUrl）；{@code /internal/resumes/{id}} 为精简版
     * （InternalResumeVO，供 lingxi-job 出题）。简历不存在时 C 返回 {@code RESUME_NOT_FOUND(3101)}，由调用方转 4303。
     */
    @GetMapping("/resumes/{id}/detail")
    Result<ResumeDetailDTO> getInternalResume(@PathVariable("id") Long id);

    /**
     * HR端候选人列表（按企业隔离，筛选/排序/分页在 C 侧完成）
     *
     * @param applicationIds 投递ID集合过滤（面试官本人负责范围；管理员可空）
     */
    @GetMapping("/applications/list")
    Result<PageResult<ApplicationDTO>> getApplicationList(@RequestParam("companyId") Long companyId,
                                                          @RequestParam(value = "applicationIds", required = false) List<Long> applicationIds,
                                                          @RequestParam(value = "status", required = false) String status,
                                                          @RequestParam(value = "jobId", required = false) Long jobId,
                                                          @RequestParam(value = "minMatchScore", required = false) BigDecimal minMatchScore,
                                                          @RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "sortBy", required = false) String sortBy,
                                                          @RequestParam(value = "page", required = false) Integer page,
                                                          @RequestParam(value = "pageSize", required = false) Integer pageSize);

    /**
     * 更新投递状态（仅 REJECTED 时传 {@code rejectFeedback}，JSON：{"reason":"原因","suggestions":["建议"]}）
     */
    @PutMapping("/applications/{id}/status")
    Result<Void> updateApplicationStatus(@PathVariable("id") Long id,
                                          @RequestParam("status") String status,
                                          @RequestParam(value = "rejectFeedback", required = false) String rejectFeedback);

    /**
     * 根据用户ID获取简历（人才推荐用）
     */
    @GetMapping("/resumes/user/{userId}")
    Result<ResumeDetailDTO> getResumeByUserId(@PathVariable("userId") Long userId);
}

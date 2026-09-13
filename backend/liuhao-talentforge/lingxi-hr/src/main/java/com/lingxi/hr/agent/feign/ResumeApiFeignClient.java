package com.lingxi.hr.agent.feign;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * lingxi-resume 用户接口 Feign 客户端（path=/api/v1）
 * <p>以求职者身份（token 透传）调 C 侧用户接口获取简历数据，供 Mock Interview 出题使用。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
@FeignClient(name = "lingxi-resume", contextId = "resumeApiFeignClient", path = "/api/v1")
public interface ResumeApiFeignClient {

    /**
     * 简历列表（取 isDefault=1 的默认简历）
     */
    @GetMapping("/resumes")
    Result<PageResult<ResumeListItemDTO>> listResumes(@RequestParam("page") int page,
                                                      @RequestParam("pageSize") int pageSize);

    /**
     * 简历详情（含 cardStructure/resumeMdUrl）
     */
    @GetMapping("/resumes/{id}")
    Result<ResumeDetailDTO> getResume(@PathVariable("id") Long id);

    /**
     * 简历能力模型（5 维评分 + subDimensions）
     */
    @GetMapping("/resumes/{id}/ability-model")
    Result<AbilityModelDTO> getAbilityModel(@PathVariable("id") Long id);
}

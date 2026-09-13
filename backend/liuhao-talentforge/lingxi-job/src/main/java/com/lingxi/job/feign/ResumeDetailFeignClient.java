package com.lingxi.job.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.job.config.InternalFeignAuthConfig;
import com.lingxi.job.feign.dto.ResumeDetailDTO;
import com.lingxi.job.feign.fallback.ResumeDetailFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * lingxi-resume 简历详情 Feign 客户端
 * <p>
 * 契约：{@code GET /api/v1/resumes/{id}} → {@link ResumeDetailDTO}（含 cardStructure）。
 * resume 无服务间鉴权（B 直连可调）；调用失败走 {@link ResumeDetailFeignFallback} 降级为
 * null，由 Interview Agent 侧降级通用题（不 503）。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-05
 */
@FeignClient(name = "lingxi-resume", contextId = "resumeDetailFeignClient", path = "/api/v1/resumes",
        configuration = InternalFeignAuthConfig.class,
        fallbackFactory = ResumeDetailFeignFallback.class)
public interface ResumeDetailFeignClient {

    /**
     * 获取简历详情（含结构化卡片）
     *
     * @param resumeId 简历ID
     * @return 简历详情（失败降级返回 null）
     */
    @GetMapping("/{id}")
    Result<ResumeDetailDTO> getResumeDetail(@PathVariable("id") Long resumeId);
}

package com.lingxi.job.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.job.config.InternalFeignAuthConfig;
import com.lingxi.job.feign.dto.InternalApplicationDTO;
import com.lingxi.job.feign.fallback.ResumeFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * lingxi-resume 投递详情 Feign 客户端
 * <p>
 * 契约：{@code GET /internal/applications/{id}} → {@link InternalApplicationDTO}。
 * 跨服务审查 P0-02：不再要求 resume 提供 interview-context 聚合接口，B 调 resume 现有
 * 投递详情接口获取关联信息（岗位/候选人/简历/企业/状态），在 lingxi-job 内二次加工。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@FeignClient(name = "lingxi-resume", path = "/internal/applications",
        configuration = InternalFeignAuthConfig.class,
        fallbackFactory = ResumeFeignFallback.class)
public interface ResumeFeignClient {

    /**
     * 获取投递详情（含岗位/候选人/简历/企业归属/状态）
     *
     * @param applicationId 投递记录ID
     * @return 投递详情（下游不可用时抛异常转 503）
     */
    @GetMapping("/{id}")
    Result<InternalApplicationDTO> getApplication(@PathVariable("id") Long applicationId);
}

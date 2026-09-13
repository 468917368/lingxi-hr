package com.lingxi.job.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.job.config.InternalFeignAuthConfig;
import com.lingxi.job.feign.dto.CandidateProfileDTO;
import com.lingxi.job.feign.fallback.CandidateProfileFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * lingxi-user 用户画像 Feign 客户端（画像推荐模式）
 * <p>
 * 契约：{@code GET /internal/users/{id}/profile} → Result（data 字段镜像到 {@link CandidateProfileDTO}）。
 * 画像缺失时 lingxi-user 以 HTTP 200 + code=1113 + data=null 返回（业务性"无画像"，fallback 不触发），
 * 由 Service 层显式判断降级；{@link CandidateProfileFeignFallback} 仅兜底异常路径（超时/网络/5xx）。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@FeignClient(name = "lingxi-user", contextId = "candidateProfileFeignClient", path = "/internal/users",
        configuration = InternalFeignAuthConfig.class,
        fallbackFactory = CandidateProfileFeignFallback.class)
public interface CandidateProfileFeignClient {

    /**
     * 查询用户画像（画像推荐模式）
     *
     * @param userId 用户ID
     * @return 统一响应，data 为用户画像推荐字段；缺失时 data=null
     */
    @GetMapping("/{userId}/profile")
    Result<CandidateProfileDTO> getUserProfile(@PathVariable("userId") Long userId);
}

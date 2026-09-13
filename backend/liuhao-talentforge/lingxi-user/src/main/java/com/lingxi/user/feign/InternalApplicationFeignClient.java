package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 内部投递接口 Feign 客户端（调用 lingxi-resume）
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@FeignClient(name = "lingxi-resume", contextId = "internalApplicationFeignClient", fallback = InternalApplicationFeignFallback.class)
public interface InternalApplicationFeignClient {

    /**
     * 更新 AI 分析结果
     *
     * @param id   投递记录ID
     * @param body {aiScore, aiAnalysis}
     */
    @PutMapping("/internal/applications/{id}/ai-analysis")
    Result<Void> updateAiAnalysis(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}

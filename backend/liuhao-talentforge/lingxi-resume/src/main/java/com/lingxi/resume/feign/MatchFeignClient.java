package com.lingxi.resume.feign;

import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 匹配度计算 Feign 客户端（调用 lingxi-user 的匹配算法）
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@FeignClient(name = "lingxi-user", fallback = MatchFeignFallback.class)
public interface MatchFeignClient {

    /**
     * 计算匹配度（返回纯分数）
     *
     * @param params {jobId, userId}
     * @return 匹配度分数 0-100
     */
    @PostMapping("/api/v1/agent/tools/match-score")
    Result<Integer> calculateMatchScore(@RequestBody Map<String, Long> params);
}

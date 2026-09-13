package com.lingxi.resume.feign;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MatchFeignClient 降级实现
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Slf4j
@Component
public class MatchFeignFallback implements MatchFeignClient {

    @Override
    public Result<Integer> calculateMatchScore(Map<String, Long> params) {
        log.warn("匹配度计算 Feign 调用失败，降级返回 0: params={}", params);
        return Result.success(0);
    }
}

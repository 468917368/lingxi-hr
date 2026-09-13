package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * InternalApplicationFeignClient 降级实现
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Slf4j
@Component
public class InternalApplicationFeignFallback implements InternalApplicationFeignClient {

    @Override
    public Result<Void> updateAiAnalysis(Long id, Map<String, Object> body) {
        log.warn("更新AI分析 Feign 调用失败，降级忽略: id={}", id);
        return Result.success();
    }
}

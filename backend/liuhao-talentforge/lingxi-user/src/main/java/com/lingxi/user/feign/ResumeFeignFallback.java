package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 简历服务 Feign 降级
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
public class ResumeFeignFallback implements ResumeFeignClient {

    @Override
    public Result<Map<String, Object>> getUserResume(Long userId) {
        log.warn("ResumeFeignFallback.getUserResume 降级: userId={}", userId);
        return Result.success(Collections.emptyMap());
    }

    @Override
    public Result<List<Long>> getAppliedJobIds(Long userId) {
        log.warn("ResumeFeignFallback.getAppliedJobIds 降级: userId={}", userId);
        return Result.success(Collections.emptyList());
    }
}

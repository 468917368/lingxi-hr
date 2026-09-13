package com.lingxi.admin.feign.fallback;

import com.lingxi.admin.feign.ResumeFeignClient;
import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 简历服务降级工厂
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Slf4j
@Component
public class ResumeFeignFallbackFactory implements FallbackFactory<ResumeFeignClient> {

    @Override
    public ResumeFeignClient create(Throwable cause) {
        log.error("ResumeFeignClient 调用失败: {}", cause.getMessage());

        return new ResumeFeignClient() {
            @Override
            public Result<Long> getApplicationCount() {
                log.warn("getApplicationCount 降级");
                return Result.error(503, "投递服务暂不可用");
            }

            @Override
            public Result<Map<String, Object>> getApplicationTrend() {
                log.warn("getApplicationTrend 降级");
                return Result.error(503, "投递服务暂不可用");
            }
        };
    }
}

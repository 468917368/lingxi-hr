package com.lingxi.admin.feign.fallback;

import com.lingxi.admin.feign.HrFeignClient;
import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * HR服务降级工厂
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Slf4j
@Component
public class HrFeignFallbackFactory implements FallbackFactory<HrFeignClient> {

    @Override
    public HrFeignClient create(Throwable cause) {
        log.error("HrFeignClient 调用失败: {}", cause.getMessage());

        return new HrFeignClient() {

            @Override
            public Result<Map<String, Object>> getCompanyCertStatus(Long id) {
                log.warn("getCompanyCertStatus 降级返回空结果");
                return Result.success(Collections.emptyMap());
            }
        };
    }
}

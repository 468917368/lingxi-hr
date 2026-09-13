package com.lingxi.job.feign.fallback;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.dto.CompanyBatchResponse;
import com.lingxi.job.feign.CompanyFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * lingxi-user 企业名批量查询降级
 * <p>
 * 系分：企业名批量查询失败 → companyName=null 兜底（不阻断主流程）。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
public class CompanyFeignFallback implements FallbackFactory<CompanyFeignClient> {

    @Override
    public CompanyFeignClient create(Throwable cause) {
        log.error("调用 lingxi-user 企业名批量查询失败，降级返回空映射: {}", cause == null ? "未知" : cause.getMessage());
        return new CompanyFeignClient() {
            @Override
            public Result<CompanyBatchResponse> batch(List<Long> companyIds) {
                // 降级：返回空 names，调用方 companyName=null 兜底
                CompanyBatchResponse empty = new CompanyBatchResponse();
                empty.setNames(Collections.emptyMap());
                return Result.success(empty);
            }
        };
    }
}

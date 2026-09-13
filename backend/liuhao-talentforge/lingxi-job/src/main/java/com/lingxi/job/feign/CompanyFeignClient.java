package com.lingxi.job.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.job.config.InternalFeignAuthConfig;
import com.lingxi.job.feign.dto.CompanyBatchResponse;
import com.lingxi.job.feign.fallback.CompanyFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * lingxi-user 企业信息 Feign 客户端（阶段3 companyName 闭环）
 * <p>
 * 契约：{@code POST /internal/companies/batch}（companyIds → names）。
 * 当前 lingxi-user 侧尚未提供该接口（greenfield 联调确认），联调前调用会走
 * {@link CompanyFeignFallback} 降级（companyName=null 兜底）。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@FeignClient(name = "lingxi-user", path = "/internal/companies",
        configuration = InternalFeignAuthConfig.class,
        fallbackFactory = CompanyFeignFallback.class)
public interface CompanyFeignClient {

    /**
     * 按企业ID批量查询企业名称
     *
     * @param companyIds 企业ID列表
     * @return 企业ID → 企业名称映射
     */
    @PostMapping("/batch")
    Result<CompanyBatchResponse> batch(@RequestBody List<Long> companyIds);
}

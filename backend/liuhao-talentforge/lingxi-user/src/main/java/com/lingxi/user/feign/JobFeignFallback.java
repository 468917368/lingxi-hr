package com.lingxi.user.feign;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 岗位服务 Feign 降级（lingxi-job 不可用时返回空数据）
 * <p>
 * 降级策略：返回空数据，不抛异常，不影响主流程。
 * 用户看到的是"暂无数据"而不是"系统错误"。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
public class JobFeignFallback implements JobFeignClient {

    @Override
    public Result<PageResult<Map<String, Object>>> searchJobs(String keyword, List<String> keywords,
            String cityCode, String industryCode, String jobType, String company, Integer page, Integer size) {
        log.warn("JobFeignFallback.searchJobs 降级: keyword={}, keywords={}, company={}", keyword, keywords, company);
        return Result.success(PageResult.of(Collections.emptyList(), 0, page, size));
    }

    @Override
    public Result<Map<String, Object>> getJobDetail(Long jobId) {
        log.warn("JobFeignFallback.getJobDetail 降级: jobId={}", jobId);
        return Result.success(Collections.emptyMap());
    }

    @Override
    public Result<List<Map<String, Object>>> listCompanyJobs(Long companyId) {
        log.warn("JobFeignFallback.listCompanyJobs 降级: companyId={}", companyId);
        return Result.success(Collections.emptyList());
    }
}

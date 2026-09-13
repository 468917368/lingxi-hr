package com.lingxi.user.feign;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 岗位服务 Feign 客户端（调用 lingxi-job 内部接口）
 * <p>
 * 接口列表：
 * - searchJobs: 岗位搜索（支持 keywords/jobType/company/city 多条件过滤）
 * - getJobDetail: 岗位详情查询
 * - listCompanyJobs: 企业岗位列表
 * </p>
 * <p>
 * 降级策略：Feign 调用失败时返回空数据（JobFeignFallback），不影响主流程
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@FeignClient(name = "lingxi-job", fallback = JobFeignFallback.class)
public interface JobFeignClient {

    /**
     * 岗位搜索（内部接口）
     */
    @GetMapping("/internal/jobs/search")
    Result<PageResult<Map<String, Object>>> searchJobs(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "keywords", required = false) List<String> keywords,
            @RequestParam(value = "cityCode", required = false) String cityCode,
            @RequestParam(value = "industryCode", required = false) String industryCode,
            @RequestParam(value = "jobType", required = false) String jobType,
            @RequestParam(value = "company", required = false) String company,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size);

    /**
     * 岗位详情（内部接口）
     */
    @GetMapping("/internal/jobs/{jobId}")
    Result<Map<String, Object>> getJobDetail(@PathVariable(value = "jobId") Long jobId);

    /**
     * 企业岗位列表（内部接口）
     */
    @GetMapping("/internal/jobs/company/{companyId}")
    Result<List<Map<String, Object>>> listCompanyJobs(@PathVariable(value = "companyId") Long companyId);
}

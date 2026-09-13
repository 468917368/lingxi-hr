package com.lingxi.admin.feign;

import com.lingxi.admin.feign.fallback.JobFeignFallbackFactory;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 岗位服务Feign客户端
 *
 * @author 成员E
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-job", path = "/internal", fallbackFactory = JobFeignFallbackFactory.class)
public interface JobFeignClient {

    /**
     * 获取岗位总数（契约返回 {data: {value: 189}}）
     */
    @GetMapping("/jobs/statistics/count")
    Result<Map<String, Object>> getPositionCount();

    /**
     * 获取今日新增岗位数（契约返回 {data: {value: 12}}）
     */
    @GetMapping("/jobs/statistics/today-count")
    Result<Map<String, Object>> getTodayPositionCount();

    /**
     * 获取岗位类型分布（契约返回 {data: {items: [{jobType, count}]}}）
     */
    @GetMapping("/jobs/statistics/distribution")
    Result<Map<String, Object>> getJobTypeDistribution();

    /**
     * 管理员岗位分页列表
     */
    @GetMapping("/admin/jobs")
    Result<Map<String, Object>> getAdminJobs(
            @RequestParam(value = "companyId", required = false) Long companyId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size);

    /**
     * 管理员岗位详情
     */
    @GetMapping("/admin/jobs/{jobId}")
    Result<Map<String, Object>> getJobDetail(@PathVariable("jobId") Long jobId);

    /**
     * 管理员违规下架岗位
     */
    @PostMapping("/admin/jobs/{jobId}/offline")
    Result<Void> offlineJob(@PathVariable("jobId") Long jobId,
                            @RequestBody Map<String, Object> request,
                            @RequestHeader("X-Operator-Id") Long operatorId);
}

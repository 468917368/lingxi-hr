package com.lingxi.admin.feign.fallback;

import com.lingxi.admin.feign.JobFeignClient;
import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 岗位服务降级工厂
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Slf4j
@Component
public class JobFeignFallbackFactory implements FallbackFactory<JobFeignClient> {

    @Override
    public JobFeignClient create(Throwable cause) {
        log.error("JobFeignClient 调用失败: {}", cause.getMessage());

        return new JobFeignClient() {
            @Override
            public Result<Map<String, Object>> getPositionCount() {
                log.warn("getPositionCount 降级返回 0");
                Map<String, Object> data = new HashMap<>();
                data.put("value", 0L);
                return Result.success(data);
            }

            @Override
            public Result<Map<String, Object>> getTodayPositionCount() {
                log.warn("getTodayPositionCount 降级返回 0");
                Map<String, Object> data = new HashMap<>();
                data.put("value", 0L);
                return Result.success(data);
            }

            @Override
            public Result<Map<String, Object>> getJobTypeDistribution() {
                log.warn("getJobTypeDistribution 降级返回空列表");
                Map<String, Object> data = new HashMap<>();
                data.put("items", Collections.emptyList());
                return Result.success(data);
            }

            @Override
            public Result<Map<String, Object>> getAdminJobs(Long companyId, String status, String keyword, Integer page, Integer size) {
                log.warn("getAdminJobs 降级返回空结果");
                return Result.success(Collections.emptyMap());
            }

            @Override
            public Result<Map<String, Object>> getJobDetail(Long jobId) {
                log.warn("getJobDetail 降级返回空结果");
                return Result.success(Collections.emptyMap());
            }

            @Override
            public Result<Void> offlineJob(Long jobId, Map<String, Object> request, Long operatorId) {
                log.error("offlineJob 降级，操作失败");
                return Result.error(503, "岗位服务暂时不可用");
            }
        };
    }
}

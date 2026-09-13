package com.lingxi.user.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.user.feign.JobFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 获取岗位详情工具
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetJobDetailTool {

    private final JobFeignClient jobFeignClient;
    private final ObjectMapper objectMapper;

    public String execute(Map<String, Object> params) {
        try {
            Long jobId = toLong(params.get("jobId"));
            if (jobId == null) return "{\"error\": \"缺少 jobId 参数\"}";

            Result<Map<String, Object>> result = jobFeignClient.getJobDetail(jobId);
            return objectMapper.writeValueAsString(result.getData());
        } catch (Exception e) {
            log.error("获取岗位详情失败", e);
            return "{\"error\": \"获取详情失败\"}";
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return val instanceof Number ? ((Number) val).longValue() : null;
    }
}

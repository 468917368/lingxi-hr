package com.lingxi.admin.feign;

import com.lingxi.admin.feign.fallback.ResumeFeignFallbackFactory;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 简历服务Feign客户端
 *
 * @author 成员E
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-resume", path = "/internal", fallbackFactory = ResumeFeignFallbackFactory.class)
public interface ResumeFeignClient {

    /**
     * 获取投递总数
     */
    @GetMapping("/applications/count")
    Result<Long> getApplicationCount();

    /**
     * 获取投递趋势数据（近7天，含今日投递数）
     */
    @GetMapping("/applications/trend")
    Result<Map<String, Object>> getApplicationTrend();
}

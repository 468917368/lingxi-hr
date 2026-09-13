package com.lingxi.admin.feign;

import com.lingxi.admin.feign.fallback.HrFeignFallbackFactory;
import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HR服务Feign客户端
 *
 * @author 成员E
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-hr", path = "/internal", fallbackFactory = HrFeignFallbackFactory.class)
public interface HrFeignClient {

    // ==================== 认证状态接口（成员D提供） ====================

    /**
     * 获取企业认证状态
     */
    @GetMapping("/companies/{id}/cert-status")
    Result<Map<String, Object>> getCompanyCertStatus(@PathVariable("id") Long id);
}

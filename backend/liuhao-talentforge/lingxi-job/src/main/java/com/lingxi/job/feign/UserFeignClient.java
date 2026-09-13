package com.lingxi.job.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.job.config.InternalFeignAuthConfig;
import com.lingxi.job.feign.dto.UserInfoDTO;
import com.lingxi.job.feign.fallback.UserFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * lingxi-user 用户信息 Feign 客户端（岗位创建人姓名展示）
 * <p>
 * 契约：{@code GET /internal/users/batch?ids=1,2,3} → Result（data 为 {@link UserInfoDTO} 列表，含 name）。
 * 仅提供批量查询（展示增强定位：一次调用，失败/缺失由 Service 统一降级为 "用户"+id，
 * 不做逐 id 单查放大请求；{@link UserFeignFallback} 兜底异常路径返回空列表）。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@FeignClient(name = "lingxi-user", contextId = "userFeignClient", path = "/internal/users",
        configuration = InternalFeignAuthConfig.class,
        fallbackFactory = UserFeignFallback.class)
public interface UserFeignClient {

    /**
     * 批量获取用户基础信息（逗号拼接 ids）
     *
     * @param ids 逗号分隔的用户ID列表，如 "1,2,3"
     * @return 统一响应，data 为用户基础信息列表（异常/缺失时由 fallback 返回空列表）
     */
    @GetMapping("/batch")
    Result<List<UserInfoDTO>> batchUsers(@RequestParam("ids") String ids);
}

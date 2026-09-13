package com.lingxi.hr.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.dto.SysUserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * lingxi-user Feign 客户端
 * <p>
 * 成员管理列表依赖：批量/单查用户信息（A 已确认契约）。
 * 创建面试官不再走本客户端，复用 {@link AuthFeignClient#register}。
 * </p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-user", contextId = "userFeignClient", path = "/internal")
public interface UserFeignClient {

    /**
     * 根据用户ID查询用户信息（含画像）
     */
    @GetMapping("/users/{id}")
    Result<SysUserDTO> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户信息
     * <p>注意：A 已确认契约但接口暂未落地，落地前 listMembers 自动降级为单查 {@link #getUserById}。</p>
     */
    @GetMapping("/users/batch")
    Result<List<SysUserDTO>> batchUsers(@RequestParam("ids") String ids);

    /**
     * 获取用户隐私设置（盲选模式等）
     */
    @GetMapping("/users/{id}/privacy")
    Result<Map<String, Object>> getUserPrivacy(@PathVariable("id") Long id);
}

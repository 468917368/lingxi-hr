package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 企业服务 Feign 客户端
 */
@FeignClient(name = "lingxi-hr", fallback = HrCompanyFeignFallback.class)
public interface HrCompanyFeignClient {

    /**
     * 按企业ID查询企业信息
     */
    @GetMapping("/internal/companies/{companyId}")
    Result<Map<String, Object>> getById(@PathVariable("companyId") Long companyId);

    /**
     * 按企业名称查询企业ID
     */
    @GetMapping("/internal/companies/by-name")
    Result<Long> getIdByName(@RequestParam("name") String name);

    /**
     * 查询用户所属企业ID
     */
    @GetMapping("/internal/companies/member/company-id")
    Result<Long> getCompanyIdByUserId(@RequestParam("userId") Long userId);

    /**
     * 查询所有HR用户ID
     */
    @GetMapping("/internal/companies/members/hr-user-ids")
    Result<List<Long>> findAllHrUserIds();
}

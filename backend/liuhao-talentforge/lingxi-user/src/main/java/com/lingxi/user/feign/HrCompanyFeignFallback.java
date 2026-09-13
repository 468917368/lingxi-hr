package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 企业服务 Feign 降级
 */
@Slf4j
@Component
public class HrCompanyFeignFallback implements HrCompanyFeignClient {

    @Override
    public Result<Map<String, Object>> getById(Long companyId) {
        log.warn("HrCompanyFeignFallback.getById 降级: companyId={}", companyId);
        return Result.success(null);
    }

    @Override
    public Result<Long> getIdByName(String name) {
        log.warn("HrCompanyFeignFallback.getIdByName 降级: name={}", name);
        return Result.success(null);
    }

    @Override
    public Result<Long> getCompanyIdByUserId(Long userId) {
        log.warn("HrCompanyFeignFallback.getCompanyIdByUserId 降级: userId={}", userId);
        return Result.success(null);
    }

    @Override
    public Result<List<Long>> findAllHrUserIds() {
        log.warn("HrCompanyFeignFallback.findAllHrUserIds 降级");
        return Result.success(Collections.emptyList());
    }
}

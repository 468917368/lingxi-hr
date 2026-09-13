package com.lingxi.user.feign;

import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 简历服务 Feign 客户端
 *
 * @author 成员A
 * @since 2026-08-05
 */
@FeignClient(name = "lingxi-resume", fallback = ResumeFeignFallback.class)
public interface ResumeFeignClient {

    /**
     * 获取用户简历摘要（脱敏）
     */
    @GetMapping("/internal/resumes/user/{userId}")
    Result<Map<String, Object>> getUserResume(@PathVariable(value = "userId") Long userId);

    /**
     * 获取用户已投递的岗位ID列表
     */
    @GetMapping("/internal/resumes/user/{userId}/applied-jobs")
    Result<List<Long>> getAppliedJobIds(@PathVariable(value = "userId") Long userId);
}

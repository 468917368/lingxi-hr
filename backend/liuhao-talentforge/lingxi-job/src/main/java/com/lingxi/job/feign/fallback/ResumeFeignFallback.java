package com.lingxi.job.feign.fallback;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.dto.InternalApplicationDTO;
import com.lingxi.job.feign.ResumeFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * lingxi-resume 投递详情降级
 * <p>
 * 系分：投递上下文不可用 → 返回降级 {@code Result.error(503)}，由上游 InterviewAgentService
 * 按 code!=200 转 HTTP 503（建流前失败）。
 * </p>
 * <p><b>不抛异常</b>：Sentinel Feign 会对 fallback 抛出的异常用 Assert 包装成
 * {@code AssertionError}（Error 类型，非 Exception），上游 {@code catch(Exception)}
 * 捕获不到，会退化为 500"系统内部错误"并掩盖下游真实错误（曾为该缺陷）。
 * 返回非 null 的降级 Result 后，语义由 code 表达，Sentinel 下绝对安全。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
public class ResumeFeignFallback implements FallbackFactory<ResumeFeignClient> {

    @Override
    public ResumeFeignClient create(Throwable cause) {
        log.error("调用 lingxi-resume 获取投递详情失败，降级为下游不可用: {}",
                cause == null ? "未知" : cause.getMessage());
        return applicationId -> Result.error(503, "简历服务不可用");
    }
}

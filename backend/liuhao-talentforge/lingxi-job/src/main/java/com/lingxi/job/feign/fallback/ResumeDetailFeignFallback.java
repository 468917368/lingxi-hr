package com.lingxi.job.feign.fallback;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.dto.ResumeDetailDTO;
import com.lingxi.job.feign.ResumeDetailFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * lingxi-resume 简历详情降级
 * <p>
 * 简历详情不可用时降级返回 {@code Result.error(500)}，由 Interview Agent 侧对
 * code!=200 跳过 → 按"简历缺失/完整度不足"降级通用题（dataCompleteness=null → 不 503），
 * 保证面试出题可用。
 * </p>
 * <p><b>不返回 null</b>：与 {@link ResumeFeignFallback} 同理，Sentinel Feign 对 fallback
 * 返回 null / 抛异常的行为存在 Assert 包装不确定性，统一返回非 null 降级 Result 最安全。</p>
 *
 * @author 成员B
 * @since 2026-08-05
 */
@Slf4j
@Component
public class ResumeDetailFeignFallback implements FallbackFactory<ResumeDetailFeignClient> {

    @Override
    public ResumeDetailFeignClient create(Throwable cause) {
        log.warn("调用 lingxi-resume 获取简历详情失败，降级为 Result.error(500): {}",
                cause == null ? "未知" : cause.getMessage());
        return resumeId -> Result.error(500, "简历解析失败");
    }
}

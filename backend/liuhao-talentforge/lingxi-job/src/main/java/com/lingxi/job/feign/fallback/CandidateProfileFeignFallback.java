package com.lingxi.job.feign.fallback;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.dto.CandidateProfileDTO;
import com.lingxi.job.feign.CandidateProfileFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * lingxi-user 用户画像查询降级
 * <p>
 * 仅覆盖异常路径（超时/网络/5xx）：返回 null → Service 识别 {@code result == null} 降级为最新排序。
 * 画像缺失的 1113（HTTP 200 正常反序列化，不抛异常）由 Service 层显式判断，fallback 不参与。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Slf4j
@Component
public class CandidateProfileFeignFallback implements FallbackFactory<CandidateProfileFeignClient> {

    @Override
    public CandidateProfileFeignClient create(Throwable cause) {
        log.warn("调用 lingxi-user 用户画像失败，降级为最新排序: {}",
                cause == null ? "未知" : cause.getMessage());
        return new CandidateProfileFeignClient() {
            @Override
            public Result<CandidateProfileDTO> getUserProfile(Long userId) {
                return null;
            }
        };
    }
}

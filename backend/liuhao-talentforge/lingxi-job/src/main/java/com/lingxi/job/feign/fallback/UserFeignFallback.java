package com.lingxi.job.feign.fallback;

import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.UserFeignClient;
import com.lingxi.job.feign.dto.UserInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * lingxi-user 用户信息查询降级
 * <p>
 * 仅覆盖异常路径（超时/网络/5xx）：返回空列表 → Service 识别后统一降级为 "用户"+id
 * （展示增强不阻断岗位页；不做逐 id 单查，避免用户服务异常时放大请求）。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@Component
public class UserFeignFallback implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.warn("调用 lingxi-user 批量查询用户失败，降级为 \"用户\"+id 文案: {}",
                cause == null ? "未知" : cause.getMessage());
        return new UserFeignClient() {
            @Override
            public Result<List<UserInfoDTO>> batchUsers(String ids) {
                return Result.success(Collections.emptyList());
            }
        };
    }
}

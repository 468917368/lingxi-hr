package com.lingxi.resume.feign;

import com.lingxi.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * HR 通知 Feign 客户端（调用 lingxi-hr 内部接口，投递成功后通知本企业 HR）
 *
 * <p>契约与成员D确认（2026-08-10）：候选人投递成功（submit/reapply）后，C 侧事务提交后
 * best-effort 调 D {@code POST /internal/applications/{id}/notify-hr}，由 D 查本企业
 * HR_ADMIN 并 Feign 直调 lingxi-chat 发送 NEW_APPLICATION 通知。失败仅告警，不影响投递闭环。
 *
 * @author 成员C
 * @since 2026-08-10
 */
@FeignClient(name = "lingxi-hr", path = "/internal", contextId = "hrNotifyFeignClient",
        fallback = HrNotifyFeignFallback.class)
public interface HrNotifyFeignClient {

    /**
     * 新投递通知 HR（D 内部接口，best-effort）
     */
    @PostMapping("/applications/{id}/notify-hr")
    Result<Void> notifyHrNewApplication(@PathVariable("id") Long applicationId);
}

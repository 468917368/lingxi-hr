package com.lingxi.resume.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.resume.feign.dto.OfferAcceptRequest;
import com.lingxi.resume.feign.dto.OfferRejectRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Offer Feign 客户端（调用 lingxi-hr 的 Offer 内部接口，同步 hr_offer 状态）
 *
 * <p>契约与成员D确认（2026-08-07）：候选人接受/拒绝 Offer 时，C 侧先本地 transition，
 * 后调 D 同步 hr_offer（SENT→ACCEPTED/REJECTED + confirm/release HC）。D 失败抛异常 → C 回滚，
 * 重试时 D 幂等自愈。/internal 经 Gateway AuthFilter 白名单放行（与 D→C 内部接口一致）。
 *
 * @author 成员C
 * @since 2026-08-07
 */
@FeignClient(name = "lingxi-hr", path = "/internal", contextId = "offerFeignClient",
        fallback = OfferFeignFallback.class)
public interface OfferFeignClient {

    /**
     * 候选人接受 Offer：D 同步 hr_offer SENT→ACCEPTED + confirm HC
     * 错误码：4200 Offer不存在 / 4201 状态不可操作 / 4202 已过期 / 4203 HC不足
     */
    @PostMapping("/offers/accept")
    Result<Void> acceptOffer(@RequestBody OfferAcceptRequest request);

    /**
     * 候选人拒绝 Offer：D 同步 hr_offer SENT→REJECTED + rejected_at + reject_reason + release HC
     * 错误码：同 acceptOffer
     */
    @PostMapping("/offers/reject")
    Result<Void> rejectOffer(@RequestBody OfferRejectRequest request);
}

package com.lingxi.resume.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.resume.feign.dto.OfferAcceptRequest;
import com.lingxi.resume.feign.dto.OfferRejectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OfferFeignClient 降级实现
 *
 * <p>Offer 同步为强依赖：D 侧不可用时必须抛异常使 C 侧事务回滚，
 * 避免投递状态已流转但 hr_offer 未同步的半程状态（与匹配度降级不同，不静默成功）。
 *
 * @author 成员C
 * @since 2026-08-07
 */
@Slf4j
@Component
public class OfferFeignFallback implements OfferFeignClient {

    @Override
    public Result<Void> acceptOffer(OfferAcceptRequest request) {
        log.warn("Offer接受同步调用失败（D 侧不可用），回滚本地事务: applicationId={}",
                request.getApplicationId());
        throw new BusinessException(503, "Offer服务暂不可用，请稍后重试");
    }

    @Override
    public Result<Void> rejectOffer(OfferRejectRequest request) {
        log.warn("Offer拒绝同步调用失败（D 侧不可用），回滚本地事务: applicationId={}",
                request.getApplicationId());
        throw new BusinessException(503, "Offer服务暂不可用，请稍后重试");
    }
}

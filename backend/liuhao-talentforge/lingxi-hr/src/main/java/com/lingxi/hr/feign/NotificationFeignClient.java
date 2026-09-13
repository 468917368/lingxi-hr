package com.lingxi.hr.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.dto.CreateNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * lingxi-chat（公共模块，成员A）通知服务 Feign 客户端
 *
 * <p>调用 {@code POST /internal/notifications} 创建候选人通知：写 {@code sys_notification} 表 +
 * 用户在线时 WebSocket 实时推送。lingxi-chat 的 {@code /internal/**} 在 AuthInterceptor 白名单内，
 * 无需携带 Token/服务凭证头。
 *
 * <p>通知 type 对齐 lingxi-chat {@code ChatConstant} 候选人侧枚举：
 * RESUME_VIEWED(简历通知) / INTERVIEW_INVITE(面试邀请) / OFFER_RECEIVED(Offer) / JOB_RECOMMEND(岗位推荐)。
 *
 * @author 成员D
 * @since 2026-08-05
 */
@FeignClient(name = "lingxi-chat", contextId = "notificationFeignClient", path = "/internal")
public interface NotificationFeignClient {

    @PostMapping("/notifications")
    Result<Map<String, Long>> createNotification(@RequestBody CreateNotificationRequest request);
}

package com.lingxi.hr.service.notify;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.NotificationFeignClient;
import com.lingxi.hr.feign.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Offer 事件通知器（Feign 直调 lingxi-chat，同 Day 3/4-5 模式）
 *
 * <p>通知 type 对齐 lingxi-chat {@code ChatConstant}：
 * 候选人侧 {@code OFFER_RECEIVED}（Offer 收到/撤回/过期/催促）、HR 侧 {@code OFFER_MANAGE}（Offer 提醒）。
 * targetType=offer，targetId=offerId（C 端投递追踪页可经 OFFER_RECEIVED 通知 targetId 跳转详情）。
 *
 * <p>best-effort 发送：Feign 失败仅记 warn 日志，返回 false，不阻塞主流程。
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferNotifier {

    /** lingxi-chat 通知类型：候选人 Offer / HR Offer 管理 */
    private static final String TYPE_OFFER_RECEIVED = "OFFER_RECEIVED";
    private static final String TYPE_OFFER_MANAGE = "OFFER_MANAGE";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NotificationFeignClient notificationFeignClient;

    /**
     * 候选人：收到 Offer（发起时）
     */
    public boolean notifyOfferReceived(Long candidateId, Long offerId, String jobTitle,
                                       Integer salary, LocalDate entryDate, LocalDateTime expiresAt) {
        StringBuilder sb = new StringBuilder("您收到一份「").append(jobTitle).append("」Offer");
        if (salary != null) {
            sb.append("，月薪 ").append(salary).append(" 元");
        }
        if (entryDate != null) {
            sb.append("，预计入职 ").append(entryDate);
        }
        if (expiresAt != null) {
            sb.append("，请于 ").append(expiresAt.format(TIME_FMT)).append(" 前确认");
        }
        return send(candidateId, TYPE_OFFER_RECEIVED, "收到 Offer", sb.toString(), offerId);
    }

    /**
     * 候选人：Offer 已撤回
     */
    public boolean notifyOfferRetracted(Long candidateId, Long offerId, String jobTitle) {
        return send(candidateId, TYPE_OFFER_RECEIVED, "Offer 已撤回",
                "「" + jobTitle + "」的 Offer 已被撤回，如有疑问请联系 HR", offerId);
    }

    /**
     * 候选人：Offer 已过期
     */
    public boolean notifyOfferExpired(Long candidateId, Long offerId, String jobTitle) {
        return send(candidateId, TYPE_OFFER_RECEIVED, "Offer 已过期",
                "「" + jobTitle + "」的 Offer 已过期，如需协商请联系 HR", offerId);
    }

    /**
     * 候选人：催促确认 Offer
     */
    public boolean notifyOfferUrge(Long candidateId, Long offerId, String jobTitle, LocalDateTime expiresAt) {
        String content = "请尽快确认「" + jobTitle + "」的 Offer"
                + (expiresAt != null ? "，有效期至 " + expiresAt.format(TIME_FMT) : "");
        return send(candidateId, TYPE_OFFER_RECEIVED, "请确认 Offer", content, offerId);
    }

    /**
     * HR：Offer 管理提醒（过期/需关注），type=OFFER_MANAGE
     */
    public boolean notifyHrManage(Long hrUserId, Long offerId, String jobTitle, String title, String content) {
        return send(hrUserId, TYPE_OFFER_MANAGE, title, content, offerId);
    }

    private boolean send(Long userId, String type, String title, String content, Long offerId) {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setUserId(userId);
        request.setType(type);
        request.setTitle(title);
        request.setContent(content);
        request.setTargetType("offer");
        request.setTargetId(offerId);

        try {
            Result<Map<String, Long>> result = notificationFeignClient.createNotification(request);
            if (result == null || !result.isSuccess()) {
                log.warn("Offer 通知发送失败: userId={}, offerId={}, type={}, code={}, message={}",
                        userId, offerId, type,
                        result != null ? result.getCode() : null,
                        result != null ? result.getMessage() : null);
                return false;
            }
            log.info("Offer 通知发送成功: userId={}, offerId={}, type={}", userId, offerId, type);
            return true;
        } catch (Exception e) {
            log.warn("Offer 通知 Feign 调用失败（不阻塞主流程）: userId={}, offerId={}, type={}, error={}",
                    userId, offerId, type, e.getMessage());
            return false;
        }
    }
}

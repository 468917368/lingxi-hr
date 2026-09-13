package com.lingxi.hr.service.notify;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.RejectFeedbackDTO;
import com.lingxi.hr.feign.NotificationFeignClient;
import com.lingxi.hr.feign.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 候选人事件通知器（Feign 直调 lingxi-chat 通知服务）
 *
 * <p>标记合适/不合适后通知候选人：调 lingxi-chat {@code POST /internal/notifications}，
 * 由公共模块写 {@code sys_notification} 表 + 用户在线时 WebSocket 实时推送。
 *
 * <p>通知 type 对齐 lingxi-chat {@code ChatConstant} 候选人侧枚举：简历筛选结果用
 * {@code RESUME_VIEWED}(简历通知)，保证 C 端 {@code /api/v1/notifications/unread-count}
 * 的 resumeViewedCount 统计口径一致。
 *
 * <p>best-effort 发送：Feign 失败仅记 warn 日志，返回 false，不阻塞标记主流程。
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateNotifier {

    /** lingxi-chat 候选人侧通知类型：简历通知 */
    private static final String TYPE_RESUME_VIEWED = "RESUME_VIEWED";

    private final NotificationFeignClient notificationFeignClient;

    /**
     * 标记合适：通过简历筛选通知
     */
    public boolean notifyScreenedPass(Long candidateId, Long applicationId, String jobTitle) {
        return send(candidateId, "简历筛选通过",
                "您的简历已通过「" + jobTitle + "」筛选，请留意后续面试安排", applicationId);
    }

    /**
     * 查看简历：通知候选人「简历被查看」
     */
    public boolean notifyResumeViewed(Long candidateId, Long applicationId, String jobTitle) {
        return send(candidateId, "简历被查看",
                "HR 已查看您投递「" + jobTitle + "」的简历，请留意后续进展", applicationId);
    }

    /**
     * 标记不合适：淘汰 + 落选反馈通知
     */
    public boolean notifyRejected(Long candidateId, Long applicationId, String jobTitle,
                                  RejectFeedbackDTO feedback) {
        String reason = feedback != null && feedback.getReason() != null
                ? feedback.getReason() : "本次未能通过简历筛选";
        return send(candidateId, "简历筛选未通过",
                "很遗憾，「" + jobTitle + "」未能通过筛选。" + reason, applicationId);
    }

    private boolean send(Long userId, String title, String content, Long applicationId) {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setUserId(userId);
        request.setType(TYPE_RESUME_VIEWED);
        request.setTitle(title);
        request.setContent(content);
        request.setTargetType("application");
        request.setTargetId(applicationId);

        try {
            Result<Map<String, Long>> result = notificationFeignClient.createNotification(request);
            if (result == null || !result.isSuccess()) {
                log.warn("候选人通知发送失败: userId={}, applicationId={}, code={}, message={}",
                        userId, applicationId,
                        result != null ? result.getCode() : null,
                        result != null ? result.getMessage() : null);
                return false;
            }
            log.info("候选人通知发送成功: userId={}, applicationId={}, type={}",
                    userId, applicationId, TYPE_RESUME_VIEWED);
            return true;
        } catch (Exception e) {
            log.warn("候选人通知 Feign 调用失败（不阻塞主流程）: userId={}, applicationId={}, error={}",
                    userId, applicationId, e.getMessage());
            return false;
        }
    }
}

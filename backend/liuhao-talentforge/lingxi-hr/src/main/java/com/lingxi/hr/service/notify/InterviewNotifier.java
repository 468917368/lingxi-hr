package com.lingxi.hr.service.notify;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.RejectFeedbackDTO;
import com.lingxi.hr.feign.NotificationFeignClient;
import com.lingxi.hr.feign.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 面试事件通知器（Feign 直调 lingxi-chat 通知服务，同 Day 3 CandidateNotifier 模式）
 *
 * <p>通知 type 对齐 lingxi-chat {@code ChatConstant}：
 * 候选人侧用 {@code INTERVIEW_INVITE}(面试邀请)、B端侧用 {@code INTERVIEW_SCHEDULE}(面试安排)。
 * 面试结果（通过/未通过）暂用 INTERVIEW_INVITE 归类（无独立"面试结果"类型，
 * TODO 待 A 扩展 ChatConstant 后拆分）。
 *
 * <p>best-effort 发送：Feign 失败仅记 warn 日志，返回 false，不阻塞主流程。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewNotifier {

    /** lingxi-chat 通知类型：候选人面试邀请 / B端面试安排 */
    private static final String TYPE_INTERVIEW_INVITE = "INTERVIEW_INVITE";
    private static final String TYPE_INTERVIEW_SCHEDULE = "INTERVIEW_SCHEDULE";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final NotificationFeignClient notificationFeignClient;

    /**
     * 候选人：面试邀请通知
     */
    public boolean notifyCandidateInvite(Long candidateId, Long interviewId, String jobTitle,
                                         LocalDateTime scheduledAt, String location) {
        StringBuilder sb = new StringBuilder("您有一场「").append(jobTitle).append("」面试");
        if (scheduledAt != null) {
            sb.append("，时间 ").append(scheduledAt.format(TIME_FMT));
        }
        if (location != null && !location.isEmpty()) {
            sb.append("，地点/链接 ").append(location);
        }
        sb.append("。请准时参加");
        return send(candidateId, TYPE_INTERVIEW_INVITE, "面试邀请", sb.toString(), interviewId);
    }

    /**
     * 面试官：面试安排通知
     */
    public boolean notifyInterviewerSchedule(Long interviewerId, Long interviewId, String jobTitle,
                                             LocalDateTime scheduledAt) {
        String content = "您被安排了一场「" + jobTitle + "」面试"
                + (scheduledAt != null ? "，时间 " + scheduledAt.format(TIME_FMT) : "");
        return send(interviewerId, TYPE_INTERVIEW_SCHEDULE, "面试安排", content, interviewId);
    }

    /**
     * 候选人：面试通过（进入 Offer 环节）
     */
    public boolean notifyPass(Long candidateId, Long interviewId, String jobTitle) {
        return send(candidateId, TYPE_INTERVIEW_INVITE, "面试通过",
                "恭喜您通过「" + jobTitle + "」面试，HR 将尽快与您沟通 Offer 细节", interviewId);
    }

    /**
     * 候选人：面试未通过（含落选反馈）
     */
    public boolean notifyReject(Long candidateId, Long interviewId, String jobTitle,
                                RejectFeedbackDTO feedback) {
        String reason = feedback != null && feedback.getReason() != null
                ? feedback.getReason() : "本次面试未能通过";
        return send(candidateId, TYPE_INTERVIEW_INVITE, "面试结果",
                "很遗憾，「" + jobTitle + "」面试未通过。" + reason, interviewId);
    }

    /**
     * HR：候选人待定，安排复面
     */
    public boolean notifyHrFollowUp(Long hrUserId, Long interviewId, String candidateName, String jobTitle) {
        return send(hrUserId, TYPE_INTERVIEW_SCHEDULE, "待安排复面",
                "候选人「" + candidateName + "」面试待定（「" + jobTitle + "」），请安排复面", interviewId);
    }

    private boolean send(Long userId, String type, String title, String content, Long interviewId) {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setUserId(userId);
        request.setType(type);
        request.setTitle(title);
        request.setContent(content);
        request.setTargetType("interview");
        request.setTargetId(interviewId);

        try {
            Result<Map<String, Long>> result = notificationFeignClient.createNotification(request);
            if (result == null || !result.isSuccess()) {
                log.warn("面试通知发送失败: userId={}, interviewId={}, type={}, code={}, message={}",
                        userId, interviewId, type,
                        result != null ? result.getCode() : null,
                        result != null ? result.getMessage() : null);
                return false;
            }
            log.info("面试通知发送成功: userId={}, interviewId={}, type={}", userId, interviewId, type);
            return true;
        } catch (Exception e) {
            log.warn("面试通知 Feign 调用失败（不阻塞主流程）: userId={}, interviewId={}, type={}, error={}",
                    userId, interviewId, type, e.getMessage());
            return false;
        }
    }
}

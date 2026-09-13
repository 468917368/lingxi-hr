package com.lingxi.hr.service.notify;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.NotificationFeignClient;
import com.lingxi.hr.feign.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 投递事件通知器（Feign 直调 lingxi-chat，同 CandidateNotifier 模式）
 *
 * <p>通知 type 对齐 lingxi-chat {@code ChatConstant} B 端枚举：
 * {@code NEW_APPLICATION}（投递通知）。HR 端「新投递」列表/未读数即统计该类型。
 *
 * <p>best-effort 发送：Feign 失败仅记 warn 日志，返回 false，不阻塞主流程。
 *
 * @author 成员D
 * @since 2026-08-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationNotifier {

    /** lingxi-chat B 端通知类型：新投递 */
    private static final String TYPE_NEW_APPLICATION = "NEW_APPLICATION";

    private final NotificationFeignClient notificationFeignClient;

    /**
     * HR：新投递通知（type=NEW_APPLICATION，targetType=application）
     */
    public boolean notifyHrNewApplication(Long hrUserId, Long applicationId, String jobTitle, String candidateName) {
        String candidate = candidateName != null && !candidateName.isEmpty() ? candidateName : "候选人";
        String title = "新投递";
        String content = "候选人「" + candidate + "」投递了「" + jobTitle + "」，请及时处理";
        return send(hrUserId, title, content, applicationId);
    }

    private boolean send(Long userId, String title, String content, Long applicationId) {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setUserId(userId);
        request.setType(TYPE_NEW_APPLICATION);
        request.setTitle(title);
        request.setContent(content);
        request.setTargetType("application");
        request.setTargetId(applicationId);

        try {
            Result<Map<String, Long>> result = notificationFeignClient.createNotification(request);
            if (result == null || !result.isSuccess()) {
                log.warn("新投递通知发送失败: userId={}, applicationId={}, code={}, message={}",
                        userId, applicationId,
                        result != null ? result.getCode() : null,
                        result != null ? result.getMessage() : null);
                return false;
            }
            log.info("新投递通知发送成功: userId={}, applicationId={}, type={}",
                    userId, applicationId, TYPE_NEW_APPLICATION);
            return true;
        } catch (Exception e) {
            log.warn("新投递通知 Feign 调用失败（不阻塞主流程）: userId={}, applicationId={}, error={}",
                    userId, applicationId, e.getMessage());
            return false;
        }
    }
}

package com.lingxi.hr.service;

/**
 * 通知服务（HR 端事件通知编排）
 *
 * <p>供内部接口 {@code /internal/applications/{id}/notify-hr} 调用：lingxi-resume
 * 投递成功后回调，由 lingxi-hr 查本企业 HR_ADMIN 后 Feign 直调 lingxi-chat 发送通知
 * （对齐 2026-08-08「D 通知走 Feign 直调 lingxi-chat」决策）。
 *
 * @author 成员D
 * @since 2026-08-10
 */
public interface HrNotificationService {

    /**
     * 新投递通知 HR（best-effort，全部失败不抛出）
     *
     * @param applicationId 投递记录ID
     */
    void notifyHrNewApplication(Long applicationId);
}

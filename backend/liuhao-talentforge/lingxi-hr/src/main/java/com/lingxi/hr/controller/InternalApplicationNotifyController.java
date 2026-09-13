package com.lingxi.hr.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.service.HrNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投递事件内部接口（供 lingxi-resume 投递成功后回调，2026-08-10）
 *
 * <p>背景：C 端候选人投递成功（submit/reapply）后，需要通知本企业 HR 有新投递。
 * 企业 HR 成员表 {@code hr_company_member} 归属 lingxi-hr，故由 C 回调本接口，
 * lingxi-hr 查 HR_ADMIN 后 Feign 直调 lingxi-chat 发送 NEW_APPLICATION 通知
 * （对齐 2026-08-08「D 通知走 Feign 直调 lingxi-chat」决策，不引入 MQ）。
 *
 * <p>best-effort：本接口只触发通知，任何失败内部吞掉，返回 success，不影响 C 投递闭环。
 * 路径 /internal/** 由 gateway 白名单免鉴权（与其它内部接口一致）；Feign 直接按服务名调用，
 * 不受 gateway /internal/applications/** 路由影响。
 *
 * @author 成员D
 * @since 2026-08-10
 */
@Slf4j
@RestController
@RequestMapping("/internal/applications")
@RequiredArgsConstructor
public class InternalApplicationNotifyController {

    private final HrNotificationService hrNotificationService;

    /**
     * 新投递通知 HR（供 lingxi-resume submit/reapply 成功后调用）
     */
    @PostMapping("/{id}/notify-hr")
    public Result<Void> notifyHrNewApplication(@PathVariable("id") Long applicationId) {
        hrNotificationService.notifyHrNewApplication(applicationId);
        return Result.success();
    }
}

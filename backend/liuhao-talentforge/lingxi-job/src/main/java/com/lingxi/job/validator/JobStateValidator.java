package com.lingxi.job.validator;

import com.lingxi.common.enums.JobStatus;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.enums.CloseReasonEnum;
import com.lingxi.job.exception.JobErrorCode;

/**
 * 岗位状态机校验器（静态方法，供 HrJobService/InternalAdminJobService 使用）
 * <p>迁移规则来源系分：DRAFT→PUBLISHED、PUBLISHED/PAUSED→CLOSED、CLOSED(HC_CONFIRMED_FULL)→PUBLISHED、
 * PUBLISHED/PAUSED→CLOSED(VIOLATION 管理员违规下架，OFFLINE)。CLOSED(VIOLATION) 不可 REOPEN（REOPEN 仅 HC_CONFIRMED_FULL）。
 * PAUSED 自动暂停/恢复、HC_CONFIRMED_FULL 满额自动关闭/释放重开 由 HC 事务（InternalJobServiceImpl reserve/confirm/release）实现，不在本校验器范围。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
public final class JobStateValidator {

    /** 关闭原因：正式HC已满（唯一可 REOPEN 的原因） */
    private static final String REOPENABLE_CLOSE_REASON = CloseReasonEnum.HC_CONFIRMED_FULL.getCode();

    private JobStateValidator() {
    }

    /**
     * 校验状态迁移合法性，非法迁移抛 2103（非法操作抛 400）
     *
     * @param currentStatus 当前岗位状态
     * @param action        目标操作：PUBLISH/CLOSE/REOPEN
     * @param closeReason   当前关闭原因（REOPEN 校验用）
     * @param availableHc   当前可用 HC（REOPEN 校验用）
     */
    public static void validateTransition(String currentStatus, String action, String closeReason, int availableHc) {
        if ("PUBLISH".equals(action)) {
            if (!JobStatus.DRAFT.getCode().equals(currentStatus)) {
                throw new BusinessException(JobErrorCode.JOB_ILLEGAL_STATE);
            }
            return;
        }
        if ("CLOSE".equals(action)) {
            if (!JobStatus.PUBLISHED.getCode().equals(currentStatus)
                    && !JobStatus.PAUSED.getCode().equals(currentStatus)) {
                throw new BusinessException(JobErrorCode.JOB_ILLEGAL_STATE);
            }
            return;
        }
        if ("REOPEN".equals(action)) {
            // 仅 CLOSED 且关闭原因为正式HC已满且当前有可用HC 时可重新开放
            if (!JobStatus.CLOSED.getCode().equals(currentStatus)
                    || !REOPENABLE_CLOSE_REASON.equals(closeReason)
                    || availableHc <= 0) {
                throw new BusinessException(JobErrorCode.JOB_ILLEGAL_STATE);
            }
            return;
        }
        if ("OFFLINE".equals(action)) {
            // 管理员违规下架：仅 PUBLISHED/PAUSED 放行；DRAFT 禁止、CLOSED 幂等（同因）由 Service 前置短路。
            // 注：availableHc 第 4 参不使用（仅 REOPEN 分支用），此处传 0 为无害占位。
            if (!JobStatus.PUBLISHED.getCode().equals(currentStatus)
                    && !JobStatus.PAUSED.getCode().equals(currentStatus)) {
                throw new BusinessException(JobErrorCode.JOB_ILLEGAL_STATE);
            }
            return;
        }
        throw new BusinessException(400, "非法的状态操作");
    }
}

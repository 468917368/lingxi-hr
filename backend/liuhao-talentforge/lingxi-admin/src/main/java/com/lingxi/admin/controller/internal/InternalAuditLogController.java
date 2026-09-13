package com.lingxi.admin.controller.internal;

import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.common.domain.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 内部接口 - 操作日志（供其他服务调用）
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/internal/admin/audit-logs")
@RequiredArgsConstructor
public class InternalAuditLogController {

    private final AdminAuditLogService auditLogService;

    /**
     * 记录操作日志
     * POST /internal/admin/audit-logs
     */
    @PostMapping
    public Result<Void> saveLog(@RequestBody AuditLogRequest request) {
        auditLogService.saveLog(
                request.getUserId(),
                null,
                request.getOperation(),
                null,
                null,
                request.getDetail(),
                request.getIp()
        );
        return Result.success();
    }

    @Data
    public static class AuditLogRequest {
        private Long userId;
        private String userName;
        private String operation;
        private String targetType;
        private Long targetId;
        private String detail;
        private String ip;
    }
}

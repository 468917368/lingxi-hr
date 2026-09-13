package com.lingxi.admin.controller;

import com.lingxi.admin.domain.vo.AuditLogVO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 操作日志控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService auditLogService;

    /**
     * 获取操作日志列表
     * GET /api/v1/admin/audit-logs?type=LOGIN&startDate=2026-07-01&endDate=2026-08-01&page=1&size=20
     */
    @RequireRole("ADMIN")
    @GetMapping
    public Result<PageResult<AuditLogVO>> getList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        String validType = (type != null && !type.isEmpty()) ? type : null;
        String validStartDate = (startDate != null && !startDate.isEmpty()) ? startDate : null;
        String validEndDate = (endDate != null && !endDate.isEmpty()) ? endDate : null;

        return Result.success(auditLogService.getAuditLogs(validType, validStartDate, validEndDate, page, size));
    }

    /**
     * 导出操作日志
     * GET /api/v1/admin/audit-logs/export?type=LOGIN
     */
    @RequireRole("ADMIN")
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws IOException {

        String validType = (type != null && !type.isEmpty()) ? type : null;
        String validStartDate = (startDate != null && !startDate.isEmpty()) ? startDate : null;
        String validEndDate = (endDate != null && !endDate.isEmpty()) ? endDate : null;

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=audit-logs.xlsx");

        auditLogService.exportAuditLogs(validType, validStartDate, validEndDate, response.getOutputStream());
    }
}

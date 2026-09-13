package com.lingxi.admin.controller.internal;

import com.lingxi.admin.domain.vo.AnnouncementReadCountVO;
import com.lingxi.admin.service.AdminAnnouncementService;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 内部接口 - 用户相关（供成员A调用）
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
public class InternalUserController {

    /**
     * 获取审核结果（供成员A查询企业认证结果）
     * GET /internal/admin/certifications/{id}/result
     */
    @GetMapping("/certifications/{id}/result")
    public Result<Map<String, Object>> getCertificationResult(@PathVariable Long id) {
        // TODO: 查询企业认证结果
        Map<String, Object> data = new HashMap<>();
        data.put("certificationId", id);
        data.put("certStatus", "PENDING");
        data.put("rejectReason", null);
        return Result.success(data);
    }

    /**
     * 批量查询用户状态（供成员A检查用户是否被禁用）
     * GET /internal/admin/users/status?ids=1,2,3
     */
    @GetMapping("/users/status")
    public Result<List<Map<String, Object>>> getUserStatus(@RequestParam String ids) {
        // TODO: 查询用户状态
        List<Map<String, Object>> result = new ArrayList<>();
        for (String idStr : ids.split(",")) {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", Long.parseLong(idStr.trim()));
            item.put("status", "ACTIVE");
            result.add(item);
        }
        return Result.success(result);
    }
}

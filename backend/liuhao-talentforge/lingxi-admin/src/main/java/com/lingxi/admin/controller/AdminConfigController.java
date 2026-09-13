package com.lingxi.admin.controller;

import com.lingxi.admin.domain.dto.ConfigUpdateDTO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.vo.ConfigVO;
import com.lingxi.admin.service.AdminConfigService;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final AdminConfigService configService;

    /**
     * 获取所有配置项
     * GET /api/v1/admin/config
     */
    @RequireRole("ADMIN")
    @GetMapping
    public Result<List<ConfigVO>> getConfigs() {
        return Result.success(configService.getConfigs());
    }

    /**
     * 更新配置项
     * PUT /api/v1/admin/config
     */
    @RequireRole("ADMIN")
    @PutMapping
    public Result<Void> updateConfigs(@Validated @RequestBody List<ConfigUpdateDTO> requests) {
        configService.updateConfigs(requests);
        return Result.success();
    }
}

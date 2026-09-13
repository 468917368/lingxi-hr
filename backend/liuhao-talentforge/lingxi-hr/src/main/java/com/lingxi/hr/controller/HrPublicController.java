package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.vo.HrPublicInfoVO;
import com.lingxi.hr.service.HrAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HR公开信息控制器（供求职者查看）
 *
 * @author lingxi-team
 * @since 2026-08-10
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrPublicController {

    private final HrAccountService hrAccountService;

    /**
     * 获取HR公开信息（不含隐私数据）
     * GET /api/v1/hr/{hrId}/public
     *
     * @param hrId HR用户ID
     * @return HR公开信息
     */
    @RequireLogin
    @RequireRole({"CANDIDATE"})
    @GetMapping("/{hrId}/public")
    public Result<HrPublicInfoVO> getHrPublicInfo(@PathVariable Long hrId) {
        return Result.success(hrAccountService.getHrPublicInfo(hrId));
    }
}

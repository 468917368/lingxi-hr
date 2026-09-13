package com.lingxi.user.controller;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.user.domain.vo.AdminUserDetailVO;
import com.lingxi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部求职者接口（供成员E管理端调用）
 *
 * @author 成员A
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/internal/candidates")
@RequiredArgsConstructor
public class InternalCandidateController {

    private final UserService userService;

    /**
     * 获取求职者列表
     * GET /internal/candidates?status=ACTIVE&keyword=张三&page=1&size=20
     */
    @GetMapping
    public Result<PageResult<AdminUserDetailVO>> getCandidates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(userService.getCandidates(status, keyword, page, size));
    }
}

package com.lingxi.admin.controller;

import com.lingxi.admin.domain.dto.AnnouncementDTO;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.admin.domain.vo.AnnouncementReadCountVO;
import com.lingxi.admin.domain.vo.AnnouncementReadRecordVO;
import com.lingxi.admin.domain.vo.AnnouncementVO;
import com.lingxi.admin.service.AdminAnnouncementService;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统公告控制器
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AdminAnnouncementService announcementService;

    /**
     * 获取公告列表
     * GET /api/v1/admin/announcements?page=1&size=20
     */
    @RequireRole("ADMIN")
    @GetMapping
    public Result<PageResult<AnnouncementVO>> getList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(announcementService.getAnnouncements(page, size));
    }

    /**
     * 创建公告
     * POST /api/v1/admin/announcements
     */
    @RequireRole("ADMIN")
    @PostMapping
    public Result<Void> create(@Validated @RequestBody AnnouncementDTO dto) {
        announcementService.createAnnouncement(dto);
        return Result.success();
    }

    /**
     * 编辑公告
     * PUT /api/v1/admin/announcements/{id}
     */
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Validated @RequestBody AnnouncementDTO dto) {
        announcementService.updateAnnouncement(id, dto);
        return Result.success();
    }

    /**
     * 发布公告
     * PATCH /api/v1/admin/announcements/{id}/publish
     */
    @RequireRole("ADMIN")
    @PatchMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        announcementService.publishAnnouncement(id);
        return Result.success();
    }

    /**
     * 撤回公告
     * PATCH /api/v1/admin/announcements/{id}/withdraw
     */
    @RequireRole("ADMIN")
    @PatchMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        announcementService.withdrawAnnouncement(id);
        return Result.success();
    }

    /**
     * 获取公告已读记录
     * GET /api/v1/admin/announcements/{id}/read-records?page=1&size=20
     */
    @RequireRole("ADMIN")
    @GetMapping("/{id}/read-records")
    public Result<PageResult<AnnouncementReadRecordVO>> getReadRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(announcementService.getReadRecords(id, page, size));
    }

    /**
     * 获取公告已读数量
     * GET /api/v1/admin/announcements/{id}/read-count
     */
    @RequireRole("ADMIN")
    @GetMapping("/{id}/read-count")
    public Result<AnnouncementReadCountVO> getReadCount(@PathVariable Long id) {
        return Result.success(announcementService.getReadCount(id));
    }
}

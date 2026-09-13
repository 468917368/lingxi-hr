package com.lingxi.admin.controller.internal;

import com.lingxi.admin.domain.entity.AnnouncementRead;
import com.lingxi.admin.domain.vo.AnnouncementReadCountVO;
import com.lingxi.admin.domain.vo.AnnouncementVO;
import com.lingxi.admin.mapper.AnnouncementMapper;
import com.lingxi.admin.mapper.AnnouncementReadMapper;
import com.lingxi.admin.service.AdminAnnouncementService;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 内部接口 - 公告（供其他服务调用）
 *
 * @author 成员E
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/internal/admin/announcements")
@RequiredArgsConstructor
public class InternalAnnouncementController {

    private final AdminAnnouncementService announcementService;
    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;

    /**
     * 获取公告列表（供成员D调用）
     * GET /internal/admin/announcements
     */
    @GetMapping
    public Result<PageResult<AnnouncementVO>> getAnnouncements(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(announcementService.getAnnouncements(page, size));
    }

    /**
     * 获取未读公告数
     * GET /internal/admin/announcements/unread-count
     */
    @GetMapping("/unread-count")
    public Result<AnnouncementReadCountVO> getUnreadCount(
            @RequestParam Long userId,
            @RequestParam String userType) {
        // 1. 查询已发布公告总数
        int totalCount = announcementMapper.countPublished();

        // 2. 查询该用户已读公告数
        int readCount = announcementReadMapper.countByUserIdAndType(userId, userType);

        // 3. 计算未读数和已读率
        AnnouncementReadCountVO vo = new AnnouncementReadCountVO();
        vo.setTotalCount(totalCount);
        vo.setReadCount(readCount);
        vo.setReadRate(totalCount > 0 ? Math.round((double) readCount / totalCount * 100.0) / 100.0 : 0.0);
        return Result.success(vo);
    }

    /**
     * 标记公告已读
     * POST /internal/admin/announcements/{id}/read
     */
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id, @RequestBody MarkReadRequest request) {
        AnnouncementRead read = new AnnouncementRead();
        read.setAnnouncementId(id);
        read.setUserId(request.getUserId());
        read.setUserType(request.getUserType());
        read.setReadAt(LocalDateTime.now());

        // 幂等插入，重复已读不会报错
        announcementReadMapper.insertIgnore(read);
        return Result.success();
    }

    @Data
    public static class MarkReadRequest {
        private Long userId;
        private String userType;
    }
}

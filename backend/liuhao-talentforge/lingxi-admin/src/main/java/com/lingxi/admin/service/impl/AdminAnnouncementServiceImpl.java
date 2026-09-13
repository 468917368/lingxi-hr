package com.lingxi.admin.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.admin.domain.dto.AnnouncementDTO;
import com.lingxi.admin.domain.entity.Announcement;
import com.lingxi.admin.domain.entity.AnnouncementRead;
import com.lingxi.admin.domain.vo.AnnouncementReadCountVO;
import com.lingxi.admin.domain.vo.AnnouncementReadRecordVO;
import com.lingxi.admin.domain.vo.AnnouncementVO;
import com.lingxi.admin.mapper.AnnouncementMapper;
import com.lingxi.admin.mapper.AnnouncementReadMapper;
import com.lingxi.admin.service.AdminAnnouncementService;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.admin.util.AdminSecurityUtil;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统公告服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnnouncementServiceImpl implements AdminAnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;
    private final AdminAuditLogService auditLogService;

    @Override
    public PageResult<AnnouncementVO> getAnnouncements(Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<Announcement> list = announcementMapper.selectList();
        PageInfo<Announcement> pageInfo = new PageInfo<>(list);

        List<AnnouncementVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAnnouncement(AnnouncementDTO dto) {
        Announcement announcement = new Announcement();
        announcement.setTitle(dto.getTitle());
        announcement.setContent(dto.getContent());
        announcement.setTargetRole(dto.getTargetRole());
        announcement.setStatus(dto.getStatus() != null ? dto.getStatus() : "DRAFT");
        // 暂时使用默认管理员ID（实际应从Token获取）
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        announcement.setCreatedBy(adminId != null ? adminId : 1L);
        announcementMapper.insert(announcement);

        auditLogService.saveLog(adminId != null ? adminId : 1L,
                AdminSecurityUtil.getCurrentAdminName(), "ANNOUNCE", "ANNOUNCEMENT",
                announcement.getId(), "创建公告：" + announcement.getTitle(),
                AdminSecurityUtil.getIp());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Long id, AnnouncementDTO dto) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(5008, "公告不存在");
        }

        announcement.setTitle(dto.getTitle());
        announcement.setContent(dto.getContent());
        announcement.setTargetRole(dto.getTargetRole());
        if (dto.getStatus() != null) {
            announcement.setStatus(dto.getStatus());
        }
        announcementMapper.updateById(announcement);

        auditLogService.saveLog(AdminSecurityUtil.getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(), "ANNOUNCE", "ANNOUNCEMENT",
                id, "编辑公告：" + dto.getTitle(), AdminSecurityUtil.getIp());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishAnnouncement(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(5008, "公告不存在");
        }
        if (!"DRAFT".equals(announcement.getStatus())) {
            throw new BusinessException(5008, "只有草稿状态的公告才能发布");
        }

        announcementMapper.updateStatus(id, "PUBLISHED");

        auditLogService.saveLog(AdminSecurityUtil.getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(), "ANNOUNCE", "ANNOUNCEMENT",
                id, "发布公告：" + announcement.getTitle(), AdminSecurityUtil.getIp());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawAnnouncement(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(5008, "公告不存在");
        }
        if (!"PUBLISHED".equals(announcement.getStatus())) {
            throw new BusinessException(5008, "只有已发布的公告才能撤回");
        }

        announcementMapper.updateStatus(id, "ARCHIVED");

        auditLogService.saveLog(AdminSecurityUtil.getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(), "ANNOUNCE", "ANNOUNCEMENT",
                id, "撤回公告：" + announcement.getTitle(), AdminSecurityUtil.getIp());
    }

    @Override
    public PageResult<AnnouncementReadRecordVO> getReadRecords(Long id, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<AnnouncementRead> list = announcementReadMapper.selectByAnnouncementId(id);
        PageInfo<AnnouncementRead> pageInfo = new PageInfo<>(list);

        List<AnnouncementReadRecordVO> voList = list.stream()
                .map(this::convertToReadRecordVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    public AnnouncementReadCountVO getReadCount(Long id) {
        long readCount = announcementReadMapper.countByAnnouncementId(id);

        AnnouncementReadCountVO vo = new AnnouncementReadCountVO();
        vo.setReadCount((int) readCount);
        vo.setTotalCount(0);
        vo.setReadRate(0.0);
        return vo;
    }

    private AnnouncementVO convertToVO(Announcement entity) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setTargetRole(entity.getTargetRole());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private AnnouncementReadRecordVO convertToReadRecordVO(AnnouncementRead entity) {
        AnnouncementReadRecordVO vo = new AnnouncementReadRecordVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUserType(entity.getUserType());
        vo.setReadAt(entity.getReadAt());
        return vo;
    }
}

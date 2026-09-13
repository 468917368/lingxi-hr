package com.lingxi.admin.service;

import com.lingxi.admin.domain.dto.AnnouncementDTO;
import com.lingxi.admin.domain.vo.AnnouncementReadCountVO;
import com.lingxi.admin.domain.vo.AnnouncementReadRecordVO;
import com.lingxi.admin.domain.vo.AnnouncementVO;
import com.lingxi.common.domain.PageResult;

/**
 * 系统公告服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminAnnouncementService {

    /**
     * 获取公告列表
     *
     * @param page 页码
     * @param size 每页条数
     * @return 公告列表分页结果
     */
    PageResult<AnnouncementVO> getAnnouncements(Integer page, Integer size);

    /**
     * 创建公告
     *
     * @param dto 公告信息
     */
    void createAnnouncement(AnnouncementDTO dto);

    /**
     * 编辑公告
     *
     * @param id  公告ID
     * @param dto 公告信息
     */
    void updateAnnouncement(Long id, AnnouncementDTO dto);

    /**
     * 发布公告
     *
     * @param id 公告ID
     */
    void publishAnnouncement(Long id);

    /**
     * 撤回公告
     *
     * @param id 公告ID
     */
    void withdrawAnnouncement(Long id);

    /**
     * 获取公告已读记录
     *
     * @param id   公告ID
     * @param page 页码
     * @param size 每页条数
     * @return 已读记录分页结果
     */
    PageResult<AnnouncementReadRecordVO> getReadRecords(Long id, Integer page, Integer size);

    /**
     * 获取公告已读数量
     *
     * @param id 公告ID
     * @return 已读统计数据
     */
    AnnouncementReadCountVO getReadCount(Long id);
}

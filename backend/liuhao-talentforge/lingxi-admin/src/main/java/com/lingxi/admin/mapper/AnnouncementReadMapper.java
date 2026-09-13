package com.lingxi.admin.mapper;

import com.lingxi.admin.domain.entity.AnnouncementRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公告已读记录Mapper
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Mapper
public interface AnnouncementReadMapper {

    /**
     * 根据公告ID查询已读记录
     *
     * @param announcementId 公告ID
     * @return 已读记录列表
     */
    List<AnnouncementRead> selectByAnnouncementId(@Param("announcementId") Long announcementId);

    /**
     * 统计公告已读数量
     *
     * @param announcementId 公告ID
     * @return 已读数量
     */
    long countByAnnouncementId(@Param("announcementId") Long announcementId);

    /**
     * 插入已读记录
     *
     * @param announcementRead 已读记录
     * @return 影响行数
     */
    int insert(AnnouncementRead announcementRead);

    /**
     * 统计用户已读公告数（已发布公告）
     *
     * @param userId   用户ID
     * @param userType 用户类型
     * @return 已读公告数
     */
    int countByUserIdAndType(@Param("userId") Long userId, @Param("userType") String userType);

    /**
     * 幂等插入已读记录（忽略重复）
     *
     * @param announcementRead 已读记录
     * @return 影响行数
     */
    int insertIgnore(AnnouncementRead announcementRead);
}

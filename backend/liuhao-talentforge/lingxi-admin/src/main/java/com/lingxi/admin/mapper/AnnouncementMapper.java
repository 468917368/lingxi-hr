package com.lingxi.admin.mapper;

import com.lingxi.admin.domain.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公告Mapper
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Mapper
public interface AnnouncementMapper {

    /**
     * 根据ID查询公告
     *
     * @param id 公告ID
     * @return 公告信息
     */
    Announcement selectById(@Param("id") Long id);

    /**
     * 查询公告列表
     *
     * @return 公告列表
     */
    List<Announcement> selectList();

    /**
     * 插入公告
     *
     * @param announcement 公告信息
     * @return 影响行数
     */
    int insert(Announcement announcement);

    /**
     * 更新公告
     *
     * @param announcement 公告信息
     * @return 影响行数
     */
    int updateById(Announcement announcement);

    /**
     * 更新公告状态
     *
     * @param id     公告ID
     * @param status 新状态
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 统计已发布公告数量
     *
     * @return 已发布公告数量
     */
    int countPublished();
}

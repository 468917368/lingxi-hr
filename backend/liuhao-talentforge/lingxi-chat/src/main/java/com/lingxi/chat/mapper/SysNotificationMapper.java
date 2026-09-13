package com.lingxi.chat.mapper;

import com.lingxi.chat.domain.entity.SysNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 通知Mapper
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Mapper
public interface SysNotificationMapper {

    /**
     * 插入通知
     *
     * @param notification 通知实体
     * @return 影响行数
     */
    int insert(SysNotification notification);

    /**
     * 根据ID查询通知
     *
     * @param id 通知ID
     * @return 通知实体
     */
    SysNotification selectById(@Param("id") Long id);

    /**
     * 分页查询用户的通知列表
     *
     * @param userId 用户ID
     * @param type   通知类型（可选）
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 通知列表
     */
    List<SysNotification> selectByUserId(@Param("userId") Long userId,
                                          @Param("type") String type,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /**
     * 统计用户的通知总数
     *
     * @param userId 用户ID
     * @param type   通知类型（可选）
     * @return 总数
     */
    long countByUserId(@Param("userId") Long userId, @Param("type") String type);

    /**
     * 统计用户的未读通知数
     *
     * @param userId 用户ID
     * @return 未读数
     */
    long countUnreadByUserId(@Param("userId") Long userId);

    /**
     * 按类型统计用户的未读通知数
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 未读数
     */
    long countUnreadByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    /**
     * 标记通知为已读
     *
     * @param id     通知ID
     * @param userId 用户ID（校验归属）
     * @return 影响行数
     */
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 标记用户所有通知为已读
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int markAllAsRead(@Param("userId") Long userId);

    /**
     * 按类型统计用户的未读通知数（一次查询返回所有类型）
     *
     * @param userId 用户ID
     * @return 每行 {type: String, cnt: Long}
     */
    List<Map<String, Object>> countUnreadGroupByType(@Param("userId") Long userId);

    /**
     * 删除用户今日的指定类型通知
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 影响行数
     */
    int deleteTodayByUserAndType(@Param("userId") Long userId, @Param("type") String type);
}

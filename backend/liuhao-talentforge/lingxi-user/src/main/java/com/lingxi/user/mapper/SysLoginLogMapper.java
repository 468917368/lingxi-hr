package com.lingxi.user.mapper;

import com.lingxi.user.domain.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志Mapper
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Mapper
public interface SysLoginLogMapper {

    /**
     * 插入登录日志
     *
     * @param loginLog 登录日志实体
     * @return 影响行数
     */
    int insert(SysLoginLog loginLog);

    /**
     * 分页查询用户登录日志
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 登录日志列表
     */
    List<SysLoginLog> selectByUserId(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计用户登录日志总数
     *
     * @param userId 用户ID
     * @return 总数
     */
    long countByUserId(@Param("userId") Long userId);

    /**
     * 获取用户最后登录时间
     *
     * @param userId 用户ID
     * @return 最后登录时间
     */
    LocalDateTime selectLastLoginTime(@Param("userId") Long userId);
}

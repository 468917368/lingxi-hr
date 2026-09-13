package com.lingxi.admin.mapper;

import com.lingxi.admin.domain.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员Mapper
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Mapper
public interface AdminUserMapper {

    /**
     * 根据ID查询管理员
     *
     * @param id 管理员ID
     * @return 管理员信息
     */
    AdminUser selectById(@Param("id") Long id);

    /**
     * 根据用户名查询管理员
     *
     * @param username 用户名
     * @return 管理员信息
     */
    AdminUser selectByUsername(@Param("username") String username);

    /**
     * 插入管理员
     *
     * @param adminUser 管理员信息
     * @return 影响行数
     */
    int insert(AdminUser adminUser);

    /**
     * 更新管理员信息
     *
     * @param adminUser 管理员信息
     * @return 影响行数
     */
    int updateById(AdminUser adminUser);
}

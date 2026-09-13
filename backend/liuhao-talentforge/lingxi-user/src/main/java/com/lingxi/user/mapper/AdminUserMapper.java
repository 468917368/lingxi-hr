package com.lingxi.user.mapper;

import com.lingxi.user.domain.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员账号表Mapper
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Mapper
public interface AdminUserMapper {

    /**
     * 根据ID查询管理员
     */
    AdminUser selectById(@Param("id") Long id);

    /**
     * 根据用户名查询管理员
     */
    AdminUser selectByUsername(@Param("username") String username);

    /**
     * 更新管理员记录
     */
    int updateById(AdminUser adminUser);
}

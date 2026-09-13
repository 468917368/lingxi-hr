package com.lingxi.user.mapper;

import com.lingxi.user.domain.entity.SysUserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户画像Mapper
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Mapper
public interface SysUserProfileMapper {

    /**
     * 根据用户ID查询画像
     *
     * @param userId 用户ID
     * @return 用户画像
     */
    SysUserProfile selectByUserId(@Param("userId") Long userId);

    /**
     * 插入画像
     *
     * @param profile 用户画像
     * @return 影响行数
     */
    int insert(SysUserProfile profile);

    /**
     * 更新画像
     *
     * @param profile 用户画像
     * @return 影响行数
     */
    int updateByUserId(SysUserProfile profile);
}

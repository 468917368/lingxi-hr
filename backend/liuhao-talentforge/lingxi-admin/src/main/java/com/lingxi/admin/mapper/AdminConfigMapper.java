package com.lingxi.admin.mapper;

import com.lingxi.admin.domain.entity.AdminConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置Mapper
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Mapper
public interface AdminConfigMapper {

    /**
     * 查询所有配置
     *
     * @return 配置列表
     */
    List<AdminConfig> selectAll();

    /**
     * 根据配置键查询
     *
     * @param configKey 配置键
     * @return 配置信息
     */
    AdminConfig selectByKey(@Param("configKey") String configKey);

    /**
     * 更新配置值
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @return 影响行数
     */
    int updateByKey(@Param("configKey") String configKey, @Param("configValue") String configValue);
}

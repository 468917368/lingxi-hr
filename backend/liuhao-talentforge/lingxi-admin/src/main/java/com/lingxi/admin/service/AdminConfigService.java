package com.lingxi.admin.service;

import com.lingxi.admin.domain.dto.ConfigUpdateDTO;
import com.lingxi.admin.domain.vo.ConfigVO;

import java.util.List;

/**
 * 系统配置服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminConfigService {

    /**
     * 获取所有配置项
     *
     * @return 配置列表
     */
    List<ConfigVO> getConfigs();

    /**
     * 更新配置项
     *
     * @param requests 配置更新请求列表
     */
    void updateConfigs(List<ConfigUpdateDTO> requests);
}

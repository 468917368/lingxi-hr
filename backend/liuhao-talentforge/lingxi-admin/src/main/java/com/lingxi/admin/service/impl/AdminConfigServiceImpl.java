package com.lingxi.admin.service.impl;

import com.lingxi.admin.domain.dto.ConfigUpdateDTO;
import com.lingxi.admin.domain.entity.AdminConfig;
import com.lingxi.admin.domain.vo.ConfigVO;
import com.lingxi.admin.mapper.AdminConfigMapper;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.admin.service.AdminConfigService;
import com.lingxi.admin.util.AdminSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminConfigServiceImpl implements AdminConfigService {

    private final AdminConfigMapper adminConfigMapper;
    private final AdminAuditLogService auditLogService;

    @Override
    public List<ConfigVO> getConfigs() {
        List<AdminConfig> list = adminConfigMapper.selectAll();
        return list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigs(List<ConfigUpdateDTO> requests) {
        // 先取出旧值，只记录真正变更的
        List<AdminConfig> oldConfigs = adminConfigMapper.selectAll();
        java.util.Map<String, String> oldMap = oldConfigs.stream()
                .collect(Collectors.toMap(AdminConfig::getConfigKey, AdminConfig::getConfigValue, (a, b) -> a));

        for (ConfigUpdateDTO dto : requests) {
            adminConfigMapper.updateByKey(dto.getConfigKey(), dto.getConfigValue());
        }

        List<String> changes = requests.stream()
                .filter(dto -> !dto.getConfigValue().equals(oldMap.get(dto.getConfigKey())))
                .map(dto -> dto.getConfigKey() + "=" + dto.getConfigValue())
                .collect(Collectors.toList());

        if (!changes.isEmpty()) {
            auditLogService.saveLog(AdminSecurityUtil.getCurrentAdminId(),
                    AdminSecurityUtil.getCurrentAdminName(), "CONFIG", "SYSTEM",
                    null, "修改系统配置：" + String.join(", ", changes),
                    AdminSecurityUtil.getIp());
        }
    }

    private ConfigVO convertToVO(AdminConfig entity) {
        ConfigVO vo = new ConfigVO();
        vo.setId(entity.getId());
        vo.setConfigKey(entity.getConfigKey());
        vo.setConfigValue(entity.getConfigValue());
        vo.setDescription(entity.getDescription());
        return vo;
    }
}

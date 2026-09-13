package com.lingxi.admin.service.impl;

import com.lingxi.admin.domain.dto.PasswordChangeDTO;
import com.lingxi.admin.domain.dto.ProfileUpdateDTO;
import com.lingxi.admin.domain.entity.AdminUser;
import com.lingxi.admin.domain.vo.AdminProfileVO;
import com.lingxi.admin.domain.vo.AuditLogVO;
import com.lingxi.admin.mapper.AdminUserMapper;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.admin.service.AdminProfileService;
import com.lingxi.admin.util.AdminSecurityUtil;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员个人中心服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProfileServiceImpl implements AdminProfileService {

    private final AdminUserMapper adminUserMapper;
    private final AdminAuditLogService auditLogService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public AdminProfileVO getProfile() {
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        if (adminId == null) {
            throw new BusinessException(401, "未登录，请先登录");
        }

        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(5001, "管理员不存在");
        }

        AdminProfileVO vo = new AdminProfileVO();
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setName(admin.getName());
        vo.setAvatar(admin.getAvatar());
        vo.setPhone(admin.getPhone());
        vo.setEmail(admin.getEmail());
        vo.setLastLoginAt(admin.getLastLoginAt());
        vo.setLastLoginIp(admin.getLastLoginIp());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateDTO dto) {
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(5001, "管理员不存在");
        }

        admin.setName(dto.getName());
        if (dto.getAvatar() != null) admin.setAvatar(dto.getAvatar());
        if (dto.getEmail() != null) admin.setEmail(dto.getEmail());
        adminUserMapper.updateById(admin);

        auditLogService.saveLog(adminId, AdminSecurityUtil.getCurrentAdminName(), "CONFIG", "ADMIN", adminId, "修改个人信息", AdminSecurityUtil.getIp());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(5010, "两次输入的新密码不一致");
        }
        if (dto.getNewPassword().length() < 8
                || !dto.getNewPassword().matches(".*[a-zA-Z].*")
                || !dto.getNewPassword().matches(".*\\d.*")) {
            throw new BusinessException(5011, "新密码格式不符合要求");
        }

        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(5001, "管理员不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), admin.getPassword())) {
            throw new BusinessException(5009, "当前密码错误");
        }

        admin.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        adminUserMapper.updateById(admin);

        auditLogService.saveLog(adminId, AdminSecurityUtil.getCurrentAdminName(), "CONFIG", "ADMIN", adminId, "修改密码", AdminSecurityUtil.getIp());
    }

    @Override
    public PageResult<AuditLogVO> getLoginLogs(Integer page, Integer size) {
        return auditLogService.getAuditLogs("LOGIN", null, null, page, size);
    }
}

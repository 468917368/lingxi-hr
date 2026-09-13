package com.lingxi.admin.service;

import com.lingxi.admin.domain.dto.PasswordChangeDTO;
import com.lingxi.admin.domain.dto.ProfileUpdateDTO;
import com.lingxi.admin.domain.vo.AdminProfileVO;
import com.lingxi.admin.domain.vo.AuditLogVO;
import com.lingxi.common.domain.PageResult;

/**
 * 管理员个人中心服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminProfileService {

    /**
     * 获取管理员个人信息
     *
     * @return 管理员详细信息
     */
    AdminProfileVO getProfile();

    /**
     * 修改管理员个人信息
     *
     * @param dto 修改信息
     */
    void updateProfile(ProfileUpdateDTO dto);

    /**
     * 修改管理员密码
     *
     * @param dto 密码修改信息
     */
    void changePassword(PasswordChangeDTO dto);

    /**
     * 获取管理员登录日志
     *
     * @param page 页码
     * @param size 每页条数
     * @return 登录日志分页结果
     */
    PageResult<AuditLogVO> getLoginLogs(Integer page, Integer size);
}

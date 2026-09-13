package com.lingxi.hr.service;

import com.lingxi.hr.domain.dto.ChangePasswordDTO;
import com.lingxi.hr.domain.dto.ChangePhoneDTO;
import com.lingxi.hr.domain.dto.SendEmailCodeDTO;
import com.lingxi.hr.domain.dto.SendPhoneCodeDTO;
import com.lingxi.hr.domain.dto.UpdateAccountProfileDTO;
import com.lingxi.hr.domain.dto.VerifyEmailDTO;
import com.lingxi.hr.domain.vo.AccountProfileVO;
import com.lingxi.hr.domain.vo.HrPublicInfoVO;

/**
 * 个人中心服务（账号信息查询/修改 + 密码修改 + 手机号/邮箱修改）
 *
 * @author 成员D
 * @since 2026-08-08
 */
public interface HrAccountService {

    /**
     * 账号信息查询（position 不展示；A 用户信息失败降级）
     */
    AccountProfileVO getProfile(Long userId, Long companyId);

    /**
     * 账号信息修改（name/avatar 走 lingxi-user，department 走本企业成员表；email/phone 走独立接口）
     */
    void updateProfile(Long userId, Long companyId, UpdateAccountProfileDTO dto);

    /**
     * 密码修改（薄转发 lingxi-user {@code POST /api/v1/auth/change-password}，D 校验两次一致）
     */
    void changePassword(Long userId, Long companyId, ChangePasswordDTO dto);

    /**
     * 发送手机号验证码（薄转发 lingxi-user，验证码发到新手机号）
     */
    void sendPhoneCode(Long userId, Long companyId, SendPhoneCodeDTO dto);

    /**
     * 验证并修改手机号（薄转发 lingxi-user，成功后重新登录）
     */
    void changePhone(Long userId, Long companyId, ChangePhoneDTO dto);

    /**
     * 发送邮箱验证码（薄转发 lingxi-user，验证码发到新邮箱）
     */
    void sendEmailCode(Long userId, Long companyId, SendEmailCodeDTO dto);

    /**
     * 验证并更新邮箱（薄转发 lingxi-user）
     */
    void verifyEmail(Long userId, Long companyId, VerifyEmailDTO dto);

    /**
     * 获取HR公开信息（不含隐私数据）
     *
     * @param hrId HR用户ID
     * @return HR公开信息
     */
    HrPublicInfoVO getHrPublicInfo(Long hrId);
}

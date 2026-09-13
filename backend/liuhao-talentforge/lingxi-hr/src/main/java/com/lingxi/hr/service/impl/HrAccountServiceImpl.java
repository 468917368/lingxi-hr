package com.lingxi.hr.service.impl;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.hr.domain.dto.ChangePasswordDTO;
import com.lingxi.hr.domain.dto.ChangePhoneDTO;
import com.lingxi.hr.domain.dto.SendEmailCodeDTO;
import com.lingxi.hr.domain.dto.SendPhoneCodeDTO;
import com.lingxi.hr.domain.dto.UpdateAccountProfileDTO;
import com.lingxi.hr.domain.dto.VerifyEmailDTO;
import com.lingxi.hr.domain.entity.HrCompany;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.domain.vo.AccountProfileVO;
import com.lingxi.hr.domain.vo.HrPublicInfoVO;
import com.lingxi.hr.exception.HrErrorCode;
import com.lingxi.hr.feign.AccountFeignClient;
import com.lingxi.hr.feign.UserFeignClient;
import com.lingxi.hr.feign.dto.ChangePasswordFeignDTO;
import com.lingxi.hr.feign.dto.ChangePhoneFeignDTO;
import com.lingxi.hr.feign.dto.SendEmailCodeFeignDTO;
import com.lingxi.hr.feign.dto.SendPhoneCodeFeignDTO;
import com.lingxi.hr.feign.dto.SysUserDTO;
import com.lingxi.hr.feign.dto.UpdateUserInfoFeignDTO;
import com.lingxi.hr.feign.dto.VerifyEmailFeignDTO;
import com.lingxi.hr.mapper.HrCompanyMapper;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.service.HrAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 个人中心服务实现
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrAccountServiceImpl implements HrAccountService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final UserFeignClient userFeignClient;
    private final AccountFeignClient accountFeignClient;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final HrCompanyMapper hrCompanyMapper;

    @Override
    public AccountProfileVO getProfile(Long userId, Long companyId) {
        requireActiveMember(companyId);

        AccountProfileVO vo = new AccountProfileVO();
        vo.setId(userId);

        // 1. 用户信息（A 失败降级，不阻塞）
        SysUserDTO user = fetchUser(userId);
        if (user != null) {
            vo.setName(user.getName());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setAvatar(user.getAvatar());
            vo.setRole(user.getRole());
        } else {
            vo.setName("用户" + userId);
            vo.setPhone("***");
        }

        // 2. 本企业成员部门
        HrCompanyMember member = hrCompanyMemberMapper.selectActiveByUserId(userId);
        if (member != null) {
            vo.setDepartment(member.getDepartment());
        }

        // 3. 企业名
        if (companyId != null) {
            HrCompany company = hrCompanyMapper.selectById(companyId);
            if (company != null) {
                vo.setCompanyName(company.getName());
            }
        }
        return vo;
    }

    @Override
    public void updateProfile(Long userId, Long companyId, UpdateAccountProfileDTO dto) {
        requireActiveMember(companyId);

        boolean hasName = dto.getName() != null && !dto.getName().trim().isEmpty();
        boolean hasAvatar = dto.getAvatar() != null && !dto.getAvatar().trim().isEmpty();
        boolean hasDepartment = dto.getDepartment() != null && !dto.getDepartment().trim().isEmpty();
        if (!hasName && !hasAvatar && !hasDepartment) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getErrorCode(), "请至少填写一项需要修改的信息");
        }

        // name/avatar 走 lingxi-user（A 校验姓名每月一次修改限制，失败透传）
        if (hasName || hasAvatar) {
            UpdateUserInfoFeignDTO req = new UpdateUserInfoFeignDTO();
            req.setName(hasName ? dto.getName().trim() : null);
            req.setAvatar(hasAvatar ? dto.getAvatar().trim() : null);
            ensureSuccess(accountFeignClient.updateUserInfo(req), userId, "更新用户信息失败");
        }

        // department 走本企业成员表
        if (hasDepartment) {
            hrCompanyMemberMapper.updateDepartment(companyId, userId, dto.getDepartment().trim());
        }
    }

    @Override
    public void changePassword(Long userId, Long companyId, ChangePasswordDTO dto) {
        requireActiveMember(companyId);

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getErrorCode(), "两次输入的新密码不一致");
        }

        ChangePasswordFeignDTO req = new ChangePasswordFeignDTO();
        req.setOldPassword(dto.getOldPassword());
        req.setNewPassword(dto.getNewPassword());
        ensureSuccess(accountFeignClient.changePassword(req), userId, "密码修改失败");
    }

    @Override
    public void sendPhoneCode(Long userId, Long companyId, SendPhoneCodeDTO dto) {
        requireActiveMember(companyId);
        SendPhoneCodeFeignDTO req = new SendPhoneCodeFeignDTO();
        req.setNewPhone(dto.getNewPhone());
        ensureSuccess(accountFeignClient.sendPhoneCode(req), userId, "发送手机号验证码失败");
    }

    @Override
    public void changePhone(Long userId, Long companyId, ChangePhoneDTO dto) {
        requireActiveMember(companyId);
        ChangePhoneFeignDTO req = new ChangePhoneFeignDTO();
        req.setNewPhone(dto.getNewPhone());
        req.setCode(dto.getCode());
        ensureSuccess(accountFeignClient.changePhone(req), userId, "修改手机号失败");
    }

    @Override
    public void sendEmailCode(Long userId, Long companyId, SendEmailCodeDTO dto) {
        requireActiveMember(companyId);
        SendEmailCodeFeignDTO req = new SendEmailCodeFeignDTO();
        req.setEmail(dto.getEmail());
        ensureSuccess(accountFeignClient.sendEmailCode(req), userId, "发送邮箱验证码失败");
    }

    @Override
    public void verifyEmail(Long userId, Long companyId, VerifyEmailDTO dto) {
        requireActiveMember(companyId);
        VerifyEmailFeignDTO req = new VerifyEmailFeignDTO();
        req.setEmail(dto.getEmail());
        req.setCode(dto.getCode());
        ensureSuccess(accountFeignClient.verifyEmail(req), userId, "更新邮箱失败");
    }

    // ==================== 私有方法 ====================

    /**
     * 校验 A 侧 Feign 返回成功，失败透传 A 的错误码/消息抛业务异常
     */
    private void ensureSuccess(Result<Void> r, Long userId, String failMsg) {
        if (r == null || !r.isSuccess()) {
            int code = r != null ? r.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode();
            String message = r != null ? r.getMessage() : failMsg + "，请重试";
            log.warn("{}: userId={}, code={}, message={}", failMsg, userId, code, message);
            throw new BusinessException(code, message);
        }
    }

    /**
     * 校验当前用户为本企业 ACTIVE 成员（HR_ADMIN/INTERVIEWER 均可），否则抛 4011
     */
    private void requireActiveMember(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompanyMember me = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, UserContext.getUserId());
        if (me == null || !STATUS_ACTIVE.equals(me.getStatus())) {
            throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
        }
    }

    /**
     * 查询用户信息，失败返回 null（不阻塞）
     */
    private SysUserDTO fetchUser(Long userId) {
        try {
            Result<SysUserDTO> r = userFeignClient.getUserById(userId);
            if (r != null && r.isSuccess() && r.getData() != null) {
                return r.getData();
            }
            log.warn("个人中心查询用户信息失败: userId={}, code={}, message={}",
                    userId, r != null ? r.getCode() : null, r != null ? r.getMessage() : null);
        } catch (Exception e) {
            log.warn("个人中心查询用户信息异常: userId={}, {}", userId, e.getMessage());
        }
        return null;
    }

    @Override
    public HrPublicInfoVO getHrPublicInfo(Long hrId) {
        // 1. 查询用户基本信息
        SysUserDTO user = fetchUser(hrId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 2. 查询企业成员信息
        HrCompanyMember member = hrCompanyMemberMapper.selectActiveByUserId(hrId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 3. 查询企业信息
        HrCompany company = hrCompanyMapper.selectById(member.getCompanyId());

        // 4. 构建公开信息（不含隐私数据）
        HrPublicInfoVO.HrPublicInfoVOBuilder builder = HrPublicInfoVO.builder()
                .id(hrId)
                .name(user.getName())
                .avatar(user.getAvatar())
                .role("HR")
                .department(member.getDepartment())
                .position(member.getTechDirection());

        // 5. 添加公司信息
        if (company != null) {
            HrPublicInfoVO.CompanyPublicInfoVO companyInfo = HrPublicInfoVO.CompanyPublicInfoVO.builder()
                    .id(company.getId())
                    .name(company.getName())
                    .industry(company.getIndustry())
                    .scale(company.getScale())
                    .description(company.getDescription())
                    .logo(company.getLogoUrl())
                    .build();
            builder.company(companyInfo);
        }

        return builder.build();
    }
}

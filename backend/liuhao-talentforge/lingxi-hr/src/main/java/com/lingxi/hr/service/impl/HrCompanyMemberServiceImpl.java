package com.lingxi.hr.service.impl;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.SmsUtil;
import com.lingxi.hr.domain.dto.HrJoinByInviteDTO;
import com.lingxi.hr.domain.dto.HrMemberDTO;
import com.lingxi.hr.domain.entity.HrCompany;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.domain.vo.HrJoinCompanyVO;
import com.lingxi.hr.domain.vo.HrMemberVO;
import com.lingxi.hr.exception.HrErrorCode;
import com.lingxi.hr.feign.AuthFeignClient;
import com.lingxi.hr.feign.UserFeignClient;
import com.lingxi.hr.feign.dto.LoginResponseDTO;
import com.lingxi.hr.feign.dto.RegisterRequestDTO;
import com.lingxi.hr.feign.dto.SysUserDTO;
import com.lingxi.hr.mapper.HrCompanyMapper;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.service.HrCompanyMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 企业成员服务实现（邀请码加入 + 成员管理）
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrCompanyMemberServiceImpl implements HrCompanyMemberService {

    private static final String ROLE_HR_ADMIN = "HR_ADMIN";
    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String CERT_STATUS_APPROVED = "APPROVED";
    /** hr_company_member.department 为 NOT NULL，邀请码加入时无部门信息默认值 */
    private static final String DEFAULT_DEPARTMENT_JOIN = "待定";

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final HrCompanyMapper hrCompanyMapper;
    private final UserFeignClient userFeignClient;
    private final AuthFeignClient authFeignClient;
    private final SmsUtil smsUtil;

    @Override
    public HrJoinCompanyVO joinByInvite(Long userId, HrJoinByInviteDTO dto) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // inviteCode 格式由 DTO @Pattern 校验
        HrCompany company = hrCompanyMapper.selectByInviteCode(dto.getInviteCode());
        if (company == null || !STATUS_ACTIVE.equals(company.getStatus())) {
            throw new BusinessException(HrErrorCode.INVITE_CODE_NOT_FOUND);
        }
        if (!CERT_STATUS_APPROVED.equals(company.getCertStatus())) {
            throw new BusinessException(HrErrorCode.COMPANY_NOT_APPROVED);
        }
        HrCompanyMember existing = hrCompanyMemberMapper.selectByCompanyAndUser(company.getId(), userId);
        if (existing != null) {
            throw new BusinessException(HrErrorCode.ALREADY_COMPANY_MEMBER);
        }

        HrCompanyMember member = new HrCompanyMember();
        member.setCompanyId(company.getId());
        member.setUserId(userId);
        member.setRole(ROLE_HR_ADMIN);
        member.setDepartment(DEFAULT_DEPARTMENT_JOIN);
        member.setInterviewCount(0);
        member.setStatus(STATUS_ACTIVE);
        hrCompanyMemberMapper.insert(member);

        HrJoinCompanyVO vo = new HrJoinCompanyVO();
        vo.setCompanyId(company.getId());
        vo.setCompanyName(company.getName());
        vo.setRole(ROLE_INTERVIEWER);
        vo.setStatus(STATUS_ACTIVE);
        return vo;
    }

    @Override
    public String refreshInviteCode(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);
        HrCompany company = hrCompanyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String newCode = generateUniqueInviteCode();
        hrCompanyMapper.updateInviteCode(companyId, newCode);
        log.info("刷新邀请码: companyId={}", companyId);
        return newCode;
    }

    @Override
    public void createMember(Long companyId, HrMemberDTO dto) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);

        // 复用 lingxi-user register 创建面试官账号（需成员A 放开 role=INTERVIEWER）
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setPhone(dto.getPhone());
        registerRequest.setCode(dto.getCode());
        registerRequest.setPassword(dto.getPassword());
        registerRequest.setName(dto.getName());
        registerRequest.setRole(ROLE_INTERVIEWER);
        Result<LoginResponseDTO> result = authFeignClient.register(registerRequest);
        if (result == null || !result.isSuccess()) {
            int code = result != null ? result.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode();
            String message = result != null ? result.getMessage() : "创建面试官失败，请重试";
            log.warn("创建面试官失败: phone={}, code={}, message={}", dto.getPhone(), code, message);
            throw new BusinessException(code, message);
        }
        Long userId = result.getData().getUser().getId();

        HrCompanyMember existing = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, userId);
        if (existing != null) {
            throw new BusinessException(HrErrorCode.MEMBER_ALREADY_EXISTS);
        }

        HrCompanyMember member = new HrCompanyMember();
        member.setCompanyId(companyId);
        member.setUserId(userId);
        member.setRole(ROLE_INTERVIEWER);
        member.setDepartment(dto.getDepartment());
        member.setTechDirection(dto.getTechDirection());
        member.setInterviewCount(0);
        member.setStatus(STATUS_ACTIVE);
        hrCompanyMemberMapper.insert(member);

        // 邀请短信（占位模板，正式模板待 SmsUtil 扩展），失败不阻塞
        try {
            smsUtil.sendVerificationCode(dto.getPhone(), dto.getPassword());
        } catch (Exception e) {
            log.warn("邀请短信发送失败: phone={}", dto.getPhone(), e);
        }
    }

    @Override
    public List<HrMemberVO> listMembers(Long companyId, String role, String status) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<HrCompanyMember> members = hrCompanyMemberMapper.selectByCompanyId(
                companyId, role, status != null ? status : STATUS_ACTIVE);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = members.stream()
                .map(HrCompanyMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUserDTO> userMap = fetchUsers(userIds);

        List<HrMemberVO> list = new ArrayList<>(members.size());
        for (HrCompanyMember m : members) {
            HrMemberVO vo = new HrMemberVO();
            vo.setId(m.getId());
            vo.setUserId(m.getUserId());
            vo.setRole(m.getRole());
            vo.setDepartment(m.getDepartment());
            vo.setTechDirection(m.getTechDirection());
            vo.setInterviewCount(m.getInterviewCount());
            vo.setStatus(m.getStatus());
            vo.setCreatedAt(m.getCreatedAt());
            SysUserDTO u = userMap.get(m.getUserId());
            if (u != null) {
                vo.setName(u.getName());
                vo.setPhone(u.getPhone());
                vo.setAvatar(u.getAvatar());
            } else {
                vo.setName("用户" + m.getUserId());
                vo.setPhone("***");
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public void removeMember(Long companyId, Long memberId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        requireHrAdmin(companyId);
        HrCompanyMember member = hrCompanyMemberMapper.selectById(memberId);
        if (member == null || !member.getCompanyId().equals(companyId)) {
            throw new BusinessException(HrErrorCode.MEMBER_NOT_FOUND);
        }
        if (ROLE_HR_ADMIN.equals(member.getRole())) {
            throw new BusinessException(HrErrorCode.MEMBER_CANNOT_REMOVE);
        }
        hrCompanyMemberMapper.deleteById(memberId);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验当前用户为本企业 HR_ADMIN，否则抛 4011
     */
    private void requireHrAdmin(Long companyId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompanyMember me = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, userId);
        if (me == null || !ROLE_HR_ADMIN.equals(me.getRole())) {
            throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
        }
    }

    /**
     * 批量查用户信息：优先 batch，失败降级循环单查，兜底返回空
     */
    private Map<Long, SysUserDTO> fetchUsers(List<Long> userIds) {
        Map<Long, SysUserDTO> map = new HashMap<>();
        try {
            String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            Result<List<SysUserDTO>> r = userFeignClient.batchUsers(ids);
            if (r != null && r.isSuccess() && r.getData() != null) {
                for (SysUserDTO u : r.getData()) {
                    if (u != null && u.getId() != null) {
                        map.put(u.getId(), u);
                    }
                }
                return map;
            }
            log.warn("批量查询用户失败: code={}, message={}",
                    r != null ? r.getCode() : null, r != null ? r.getMessage() : null);
        } catch (Exception e) {
            log.warn("批量查询用户异常，降级单查: {}", e.getMessage());
        }
        // 降级：循环单查
        for (Long id : userIds) {
            try {
                Result<SysUserDTO> r = userFeignClient.getUserById(id);
                if (r != null && r.isSuccess() && r.getData() != null) {
                    map.put(id, r.getData());
                }
            } catch (Exception e) {
                log.warn("单查用户失败: id={}, {}", id, e.getMessage());
            }
        }
        return map;
    }

    /**
     * 生成 6 位唯一邀请码（大写字母+数字），循环查重
     */
    private String generateUniqueInviteCode() {
        Random random = new Random();
        for (int i = 0; i < MAX_INVITE_CODE_ATTEMPTS; i++) {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int j = 0; j < INVITE_CODE_LENGTH; j++) {
                sb.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (hrCompanyMapper.selectByInviteCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "邀请码生成失败，请重试");
    }
}

package com.lingxi.hr.service;

import com.lingxi.hr.domain.dto.HrJoinByInviteDTO;
import com.lingxi.hr.domain.dto.HrMemberDTO;
import com.lingxi.hr.domain.vo.HrJoinCompanyVO;
import com.lingxi.hr.domain.vo.HrMemberVO;

import java.util.List;

/**
 * 企业成员服务接口（邀请码加入 + 成员管理）
 *
 * @author 成员D
 * @since 2026-08-02
 */
public interface HrCompanyMemberService {

    /**
     * 邀请码加入企业（加入者成为企业管理员 HR_ADMIN）
     */
    HrJoinCompanyVO joinByInvite(Long userId, HrJoinByInviteDTO dto);

    /**
     * 刷新企业邀请码（仅 HR_ADMIN）
     */
    String refreshInviteCode(Long companyId);

    /**
     * 创建面试官（HR 填写注册表单，复用 lingxi-user register 建号后加入企业）
     */
    void createMember(Long companyId, HrMemberDTO dto);

    /**
     * 成员列表（按企业过滤，可空 role/status）
     */
    List<HrMemberVO> listMembers(Long companyId, String role, String status);

    /**
     * 移除面试官（仅 HR_ADMIN，硬删除成员关系）
     */
    void removeMember(Long companyId, Long memberId);
}

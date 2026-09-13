package com.lingxi.hr.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.HrJoinByInviteDTO;
import com.lingxi.hr.domain.dto.HrMemberDTO;
import com.lingxi.hr.domain.vo.HrJoinCompanyVO;
import com.lingxi.hr.domain.vo.HrMemberVO;
import com.lingxi.hr.service.HrCompanyMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业成员控制器（邀请码加入 + 成员管理）
 * <p>
 * 对应 B 端公司管理页「公司信息/成员管理」Tab。
 * 邀请码加入：用户无企业时凭邀请码加入（仅需登录态）；
 * 其余成员操作需当前用户为本企业 HR_ADMIN。
 * </p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/company/members")
@RequiredArgsConstructor
@RequireLogin
public class HrCompanyMemberController {

    private final HrCompanyMemberService hrCompanyMemberService;

    /**
     * 邀请码加入企业
     */
    @PostMapping("/join-by-invite")
    public Result<HrJoinCompanyVO> joinByInvite(@Validated @RequestBody HrJoinByInviteDTO dto) {
        return Result.success(hrCompanyMemberService.joinByInvite(UserContext.getUserId(), dto));
    }

    /**
     * 刷新企业邀请码（HR_ADMIN）
     */
    @RequireRole("HR")
    @PutMapping("/invite-code")
    public Result<String> refreshInviteCode() {
        return Result.success(hrCompanyMemberService.refreshInviteCode(UserContext.getCompanyId()));
    }

    /**
     * 成员列表
     */
    @GetMapping
    public Result<List<HrMemberVO>> listMembers(@RequestParam(required = false) String role,
                                                @RequestParam(required = false) String status) {
        return Result.success(hrCompanyMemberService.listMembers(UserContext.getCompanyId(), role, status));
    }

    /**
     * 创建面试官（HR 填写注册表单，复用 lingxi-user register 建号后加入企业）
     */
    @RequireRole("HR")
    @PostMapping
    public Result<Void> createMember(@Validated @RequestBody HrMemberDTO dto) {
        hrCompanyMemberService.createMember(UserContext.getCompanyId(), dto);
        return Result.success();
    }

    /**
     * 移除面试官（HR_ADMIN，硬删除成员关系）
     */
    @RequireRole("HR")
    @DeleteMapping("/{memberId}")
    public Result<Void> removeMember(@PathVariable Long memberId) {
        hrCompanyMemberService.removeMember(UserContext.getCompanyId(), memberId);
        return Result.success();
    }
}

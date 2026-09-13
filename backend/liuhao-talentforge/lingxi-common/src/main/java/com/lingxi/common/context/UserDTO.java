package com.lingxi.common.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户上下文DTO
 * <p>
 * 存储从JWT Token中解析出的用户信息。
 * Token Payload设计：{userId, phone, role, companyId, certStatus}
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 角色：CANDIDATE/HR/INTERVIEWER/ADMIN */
    private String role;

    /** 企业ID（HR/面试官有值） */
    private Long companyId;

    /** 认证状态（HR有值） */
    private String certStatus;

    /** 用户状态：ACTIVE/DISABLED */
    private String status;

    /**
     * 判断是否为HR角色（HR或面试官）
     */
    public boolean isHrRole() {
        return "HR".equals(role) || "INTERVIEWER".equals(role);
    }

    /**
     * 判断是否为管理员
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /**
     * 判断是否为求职者
     */
    public boolean isCandidate() {
        return "CANDIDATE".equals(role);
    }
}

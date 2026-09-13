package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业成员表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrCompanyMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 用户ID */
    private Long userId;

    /** 角色：HR_ADMIN/INTERVIEWER */
    private String role;

    /** 所属部门 */
    private String department;

    /** 技术方向 */
    private String techDirection;

    /** 累计面试场次 */
    private Integer interviewCount;

    /** 状态：ACTIVE/DISABLED */
    private String status;

    /** 加入时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

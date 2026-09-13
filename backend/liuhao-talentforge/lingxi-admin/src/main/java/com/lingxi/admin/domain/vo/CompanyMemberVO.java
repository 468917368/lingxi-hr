package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 企业成员VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CompanyMemberVO {

    /** 成员ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 角色：HR_ADMIN/INTERVIEWER */
    private String role;

    /** 部门 */
    private String department;

    /** 面试次数 */
    private Integer interviewCount;

    /** 状态 */
    private String status;

    /** 加入时间 */
    private LocalDateTime createdAt;
}

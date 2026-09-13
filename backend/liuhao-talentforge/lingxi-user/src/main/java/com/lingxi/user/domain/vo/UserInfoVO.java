package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户信息视图对象
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 手机号 */
    private String phone;

    /** 姓名 */
    private String name;

    /** 姓名最后修改时间（用于判断是否可修改） */
    private String nameUpdatedAt;

    /** 角色 */
    private String role;

    /** 头像 */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 企业ID（HR/面试官有值，求职者/管理员为null） */
    private Long companyId;

    /** 企业信息（HR/面试官登录时返回，求职者返回null） */
    private CompanyVO company;

    /** 画像信息（嵌套） */
    private UserProfileVO profile;
}

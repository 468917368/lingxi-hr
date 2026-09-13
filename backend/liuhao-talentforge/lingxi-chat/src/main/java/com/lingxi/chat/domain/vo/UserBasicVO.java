package com.lingxi.chat.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户基础信息视图对象（Feign调用用户服务返回）
 *
 * @author 成员A
 * @since 2026-08-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBasicVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /** 角色：CANDIDATE/HR */
    private String role;

    /** 企业名称（HR有值） */
    private String companyName;
}

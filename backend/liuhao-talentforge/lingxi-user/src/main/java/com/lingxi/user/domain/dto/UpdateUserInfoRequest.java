package com.lingxi.user.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 更新用户基本信息请求
 * <p>
 * 对齐前端 services/user.ts UpdateUserInfoRequest
 * 注意：手机号(phone)为只读字段，PRD明确要求不可修改，不包含在此DTO中
 * 注意：email不在此处更新，需通过邮箱验证流程
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class UpdateUserInfoRequest {

    /** 姓名 */
    @Size(min = 1, max = 32, message = "姓名长度须在1-32字符之间")
    private String name;

    /** 头像URL */
    private String avatar;

    /** 性别：MALE/FEMALE */
    private String gender;

    /** 城市 */
    private String city;

    /** 工作年限：FRESH/1-3/3-5/5-10/10+ */
    private String workYears;

    /** 学历：COLLEGE/BACHELOR/MASTER/PHD */
    private String education;

    /** 求职状态：JOB_SEEKING/EMPLOYED_LOOKING/NOT_LOOKING */
    private String jobStatus;
}

package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 企业列表项VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CompanyVO {

    /** 企业ID */
    private Long id;

    /** 企业名称 */
    private String name;

    /** 行业 */
    private String industry;

    /** 规模 */
    private String scale;

    /** 地址 */
    private String address;

    /** 认证状态 */
    private String certStatus;

    /** 岗位数 */
    private Integer jobCount;

    /** HR数 */
    private Integer hrCount;

    /** 注册时间 */
    private LocalDateTime registerTime;
}

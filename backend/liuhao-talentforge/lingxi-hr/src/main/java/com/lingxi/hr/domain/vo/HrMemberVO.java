package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业成员 VO
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String name;
    private String phone;
    private String avatar;
    private String role;
    private String department;
    private String techDirection;
    private Integer interviewCount;
    private String status;
    private LocalDateTime createdAt;
}

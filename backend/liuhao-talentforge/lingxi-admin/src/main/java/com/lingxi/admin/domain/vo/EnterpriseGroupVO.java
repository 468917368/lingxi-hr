package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 企业分组通用VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class EnterpriseGroupVO<T> {

    /** 企业ID */
    private Long companyId;

    /** 企业名称 */
    private String companyName;

    /** 用户/岗位列表 */
    private List<T> users;
}

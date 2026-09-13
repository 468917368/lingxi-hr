package com.lingxi.job.feign.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 企业名批量查询响应 DTO（Feign 调 lingxi-user）
 * <p>
 * {@code companyId → companyName} 映射。字段随 lingxi-user 团队契约确认
 * （当前 lingxi-user 未提供 /internal/companies/batch，为 Feign 骨架预留）。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class CompanyBatchResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID → 企业名称 */
    private Map<Long, String> names;
}

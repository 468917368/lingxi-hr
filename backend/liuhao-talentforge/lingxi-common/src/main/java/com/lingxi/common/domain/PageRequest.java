package com.lingxi.common.domain;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serializable;

/**
 * 分页请求基类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，默认1 */
    @Min(value = 1, message = "页码不能小于1")
    private int page = 1;

    /** 每页条数，默认20 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize = 20;

    /** 排序字段 */
    private String sortBy;

    /** 排序方向：ASC/DESC */
    private String sortOrder = "DESC";

    /**
     * 获取排序字段（安全处理）
     */
    public String getSafeSortBy(String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }
        // 只允许字母、数字、下划线
        if (!sortBy.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return defaultField;
        }
        return sortBy;
    }

    /**
     * 获取排序方向（安全处理）
     */
    public String getSafeSortOrder() {
        if ("ASC".equalsIgnoreCase(sortOrder)) {
            return "ASC";
        }
        return "DESC";
    }
}

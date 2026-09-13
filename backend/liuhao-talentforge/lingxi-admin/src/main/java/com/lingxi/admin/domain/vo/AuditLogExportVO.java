package com.lingxi.admin.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 操作日志导出VO
 *
 * @author 成员E
 * @since 2026-08-04
 */
@Data
public class AuditLogExportVO {

    @ExcelProperty("日志ID")
    @ColumnWidth(10)
    private Long id;

    @ExcelProperty("操作用户ID")
    @ColumnWidth(12)
    private Long userId;

    @ExcelProperty("操作用户姓名")
    @ColumnWidth(15)
    private String userName;

    @ExcelProperty("操作类型")
    @ColumnWidth(12)
    private String operation;

    @ExcelProperty("操作对象类型")
    @ColumnWidth(15)
    private String targetType;

    @ExcelProperty("操作对象ID")
    @ColumnWidth(12)
    private Long targetId;

    @ExcelProperty("操作详情")
    @ColumnWidth(40)
    private String detail;

    @ExcelProperty("操作IP")
    @ColumnWidth(15)
    private String ip;

    @ExcelProperty("操作时间")
    @ColumnWidth(20)
    private String createdAt;
}

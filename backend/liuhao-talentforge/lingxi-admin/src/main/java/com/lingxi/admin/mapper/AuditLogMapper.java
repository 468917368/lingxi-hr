package com.lingxi.admin.mapper;

import com.lingxi.admin.domain.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志Mapper
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 条件查询操作日志
     *
     * @param operation 操作类型（可选）
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）
     * @param endDate 结束日期（可选，格式：yyyy-MM-dd）
     * @return 操作日志列表
     */
    List<AuditLog> selectByCondition(@Param("operation") String operation,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);

    /**
     * 插入操作日志
     *
     * @param auditLog 操作日志
     * @return 影响行数
     */
    int insert(AuditLog auditLog);
}

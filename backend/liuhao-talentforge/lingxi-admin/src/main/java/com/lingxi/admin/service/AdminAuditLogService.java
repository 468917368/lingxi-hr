package com.lingxi.admin.service;

import com.lingxi.admin.domain.vo.AuditLogVO;
import com.lingxi.common.domain.PageResult;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 操作日志服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminAuditLogService {

    /**
     * 获取操作日志列表
     *
     * @param type      操作类型筛选
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param page      页码
     * @param size      每页条数
     * @return 操作日志分页结果
     */
    PageResult<AuditLogVO> getAuditLogs(String type, String startDate, String endDate, Integer page, Integer size);

    /**
     * 导出操作日志
     *
     * @param type         操作类型筛选
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param outputStream 输出流
     */
    void exportAuditLogs(String type, String startDate, String endDate, java.io.OutputStream outputStream) throws IOException;

    /**
     * 记录操作日志
     *
     * @param userId     操作用户ID
     * @param userName   操作用户姓名
     * @param operation  操作类型
     * @param targetType 操作对象类型
     * @param targetId   操作对象ID
     * @param detail     操作详情
     * @param ip         操作IP
     */
    void saveLog(Long userId, String userName, String operation, String targetType, Long targetId, String detail, String ip);
}

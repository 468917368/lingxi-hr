package com.lingxi.admin.service.impl;

import com.alibaba.excel.EasyExcel;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.admin.domain.entity.AuditLog;
import com.lingxi.admin.domain.vo.AuditLogExportVO;
import com.lingxi.admin.domain.vo.AuditLogVO;
import com.lingxi.admin.mapper.AuditLogMapper;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.common.domain.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogVO> getAuditLogs(String type, String startDate, String endDate, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<AuditLog> list = auditLogMapper.selectByCondition(type, startDate, endDate);
        PageInfo<AuditLog> pageInfo = new PageInfo<>(list);

        List<AuditLogVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    public void exportAuditLogs(String type, String startDate, String endDate, java.io.OutputStream outputStream) throws IOException {
        // 1. 查询数据（不分页，导出全部）
        List<AuditLog> list = auditLogMapper.selectByCondition(type, startDate, endDate);

        // 2. 转换为导出格式
        List<AuditLogExportVO> exportList = list.stream()
                .map(this::convertToExportVO)
                .collect(Collectors.toList());

        // 3. 写出Excel
        EasyExcel.write(outputStream, AuditLogExportVO.class)
                .sheet("操作日志")
                .doWrite(exportList);

        log.info("导出操作日志成功，共{}条记录", exportList.size());
    }

    @Override
    public void saveLog(Long userId, String userName, String operation, String targetType, Long targetId, String detail, String ip) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUserName(userName);
        auditLog.setOperation(operation);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLog.setIp(ip);
        auditLogMapper.insert(auditLog);
    }

    private AuditLogVO convertToVO(AuditLog entity) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUserName(entity.getUserName());
        vo.setCreateTime(entity.getCreatedAt());
        vo.setOperationType(entity.getOperation());
        vo.setDetail(entity.getDetail());
        vo.setIp(entity.getIp());
        return vo;
    }

    private AuditLogExportVO convertToExportVO(AuditLog entity) {
        AuditLogExportVO vo = new AuditLogExportVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUserName("");
        vo.setOperation(entity.getOperation());
        vo.setTargetType("");
        vo.setTargetId(null);
        vo.setDetail(entity.getDetail());
        vo.setIp(entity.getIp());
        vo.setCreatedAt(entity.getCreatedAt() != null
                ? entity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "");
        return vo;
    }
}

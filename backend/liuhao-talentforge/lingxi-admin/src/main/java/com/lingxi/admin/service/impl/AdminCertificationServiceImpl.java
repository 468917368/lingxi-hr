package com.lingxi.admin.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.admin.domain.dto.RejectDTO;
import com.lingxi.admin.domain.vo.*;
import com.lingxi.admin.mapper.HrCompanyMapper;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.admin.service.AdminCertificationService;
import com.lingxi.admin.util.AdminSecurityUtil;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 企业认证审核服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCertificationServiceImpl implements AdminCertificationService {

    private final HrCompanyMapper hrCompanyMapper;
    private final AdminAuditLogService auditLogService;

    @Override
    public CertificationStatsVO getCertificationStats() {
        Map<String, Object> stats = hrCompanyMapper.countByStatus();
        CertificationStatsVO vo = new CertificationStatsVO();
        vo.setPendingCount(toInt(stats.get("pendingCount")));
        vo.setApprovedCount(toInt(stats.get("approvedCount")));
        vo.setRejectedCount(toInt(stats.get("rejectedCount")));
        vo.setWeekNewCount(toInt(stats.get("weekNewCount")));
        return vo;
    }

    @Override
    public PageResult<CertificationVO> getCertifications(String status, String keyword, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<Map<String, Object>> list = hrCompanyMapper.selectCertifications(status, keyword);
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(list);

        List<CertificationVO> voList = new ArrayList<>();
        for (Map<String, Object> item : list) {
            CertificationVO vo = new CertificationVO();
            vo.setId(toLong(item.get("id")));
            vo.setCompanyName((String) item.get("companyName"));
            vo.setIndustry((String) item.get("industry"));
            vo.setScale((String) item.get("scale"));
            vo.setAddress((String) item.get("address"));
            vo.setApplicantName((String) item.get("applicantName"));
            vo.setContactPhone((String) item.get("contactPhone"));
            vo.setLicenseUrl((String) item.get("licenseUrl"));
            vo.setCertMaterialUrl((String) item.get("certMaterialUrl"));
            vo.setStatus((String) item.get("certStatus"));
            vo.setApplyTime(toLocalDateTime(item.get("applyTime")));
            voList.add(vo);
        }

        return PageResult.of(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    public CertificationDetailVO getCertificationDetail(Long id) {
        Map<String, Object> company = hrCompanyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(5100, "认证申请不存在");
        }

        CertificationDetailVO vo = new CertificationDetailVO();
        vo.setId(id);
        vo.setCompanyName((String) company.get("name"));
        vo.setIndustry((String) company.get("industry"));
        vo.setScale((String) company.get("scale"));
        vo.setAddress((String) company.get("address"));
        vo.setContactPerson((String) company.get("contactPerson"));
        vo.setContactPhone((String) company.get("contactPhone"));
        // 优先用认证表的营业执照
        String bizLicense = (String) company.get("certBusinessLicenseUrl");
        vo.setLicenseUrl(bizLicense != null ? bizLicense : (String) company.get("businessLicenseUrl"));
        vo.setCertMaterialUrl((String) company.get("certMaterialUrl"));
        vo.setCertStatus((String) company.get("cert_status"));
        vo.setCertRejectReason((String) company.get("certRejectReason"));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Map<String, Object> company = hrCompanyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(5100, "认证申请不存在");
        }
        if (!"PENDING".equals(company.get("cert_status"))) {
            throw new BusinessException(5102, "该申请已被处理");
        }

        int rows = hrCompanyMapper.approve(id);
        if (rows == 0) {
            throw new BusinessException(5102, "操作失败，请刷新重试");
        }

        // 记录操作日志
        auditLogService.saveLog(
                getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(),
                "REVIEW",
                "COMPANY",
                id,
                "通过企业认证审核：" + company.get("name"),
                AdminSecurityUtil.getIp());

        log.info("审核通过: id={}, company={}", id, company.get("name"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, RejectDTO dto) {
        Map<String, Object> company = hrCompanyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(5100, "认证申请不存在");
        }
        if (!"PENDING".equals(company.get("cert_status"))) {
            throw new BusinessException(5102, "该申请已被处理");
        }

        int rows = hrCompanyMapper.reject(id, dto.getReason());
        if (rows == 0) {
            throw new BusinessException(5102, "操作失败，请刷新重试");
        }

        // 记录操作日志
        auditLogService.saveLog(
                getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(),
                "REVIEW",
                "COMPANY",
                id,
                "拒绝企业认证审核：" + company.get("name") + "，原因：" + dto.getReason(),
                AdminSecurityUtil.getIp());

        log.info("审核拒绝: id={}, reason={}", id, dto.getReason());
    }

    private Integer toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(" ", "T"));
    }

    /**
     * 获取当前登录管理员ID（带容错）
     * 优先从 AdminSecurityUtil 获取，如果为空则从 UserContext 获取
     *
     * @return 管理员ID，如果未登录则返回 0L（系统操作）
     */
    private Long getCurrentAdminId() {
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        if (adminId != null) {
            return adminId;
        }
        // 降级到 UserContext
        Long userId = UserContext.getUserId();
        if (userId != null) {
            return userId;
        }
        // 都为空时返回系统默认ID
        log.warn("无法获取当前管理员ID，使用系统默认ID");
        return 0L;
    }
}

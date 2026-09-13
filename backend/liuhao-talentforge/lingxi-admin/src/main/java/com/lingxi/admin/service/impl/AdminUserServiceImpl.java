package com.lingxi.admin.service.impl;

import com.lingxi.admin.domain.vo.*;
import com.lingxi.admin.mapper.HrDataMapper;
import com.lingxi.admin.mapper.SysUserMapper;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.admin.service.AdminUserService;
import com.lingxi.admin.util.AdminSecurityUtil;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final HrDataMapper hrDataMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminAuditLogService auditLogService;

    @Override
    public List<EnterpriseGroupVO<HRUserVO>> getHRUsers() {
        // 直接查数据库 hr_company_member 表
        List<Map<String, Object>> list = hrDataMapper.listHRByEnterprise();
        return groupByEnterprise(list, this::mapToHRUserVO);
    }

    @Override
    public List<EnterpriseGroupVO<InterviewerVO>> getInterviewers() {
        // 直接查数据库 hr_company_member 表
        List<Map<String, Object>> list = hrDataMapper.listInterviewersByEnterprise();
        return groupByEnterprise(list, this::mapToInterviewerVO);
    }

    @Override
    public PageResult<CandidateVO> getCandidates(String status, String keyword, Integer page, Integer size) {
        // 直接查数据库
        List<Map<String, Object>> list = sysUserMapper.selectCandidates(status, keyword);

        int total = list.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = fromIndex < total ? list.subList(fromIndex, toIndex) : new ArrayList<>();

        List<CandidateVO> voList = new ArrayList<>();
        for (Map<String, Object> item : pageList) {
            CandidateVO vo = new CandidateVO();
            vo.setId(toLong(item.get("id")));
            vo.setName(nullToDefault((String) item.get("name"), "未知用户"));
            String phone = (String) item.get("phone");
            if (phone != null && phone.length() == 11) {
                phone = phone.substring(0, 3) + "****" + phone.substring(7);
            }
            vo.setPhone(phone != null ? phone : "-");
            vo.setEmail(nullToDefault((String) item.get("email"), "-"));
            vo.setCity((String) item.get("city"));
            vo.setJobStatus((String) item.get("jobStatus"));
            vo.setExpectedPosition((String) item.get("desiredJob"));
            vo.setResumeCount(toInt(item.get("resumeCount")));
            vo.setApplyCount(toInt(item.get("applyCount")));
            vo.setStatus((String) item.get("status"));
            vo.setRegisterTime(toLocalDateTime(item.get("created_at")));
            voList.add(vo);
        }

        return PageResult.of(voList, (long) total, page, size);
    }

    @Override
    public CandidateDetailVO getCandidateDetail(Long id) {
        Map<String, Object> data = sysUserMapper.selectById(id);
        if (data == null) {
            throw new BusinessException(5200, "用户不存在");
        }
        CandidateDetailVO vo = new CandidateDetailVO();
        vo.setId(id);
        vo.setName((String) data.get("name"));
        vo.setPhone((String) data.get("phone"));
        vo.setEmail((String) data.get("email"));
        vo.setStatus((String) data.get("status"));
        vo.setRegisterTime(toLocalDateTime(data.get("created_at")));
        // 求职者补充画像
        if ("CANDIDATE".equals(data.get("role"))) {
            Map<String, Object> profile = sysUserMapper.selectProfileByUserId(id);
            if (profile != null) {
                vo.setCity((String) profile.get("city"));
                vo.setJobStatus((String) profile.get("jobStatus"));
                vo.setExpectedPosition((String) profile.get("desiredJob"));
                vo.setExpectedCity((String) profile.get("desiredCity"));
                vo.setExpectedSalary(formatSalary(profile.get("desiredSalaryMin"), profile.get("desiredSalaryMax")));
            }
            vo.setResumeCount(sysUserMapper.countResumesByUserId(id));
            vo.setApplicationCount(sysUserMapper.countApplicationsByUserId(id));
        }
        return vo;
    }

    @Override
    public PageResult<ApplicationRecordVO> getCandidateApplications(Long id, Integer page, Integer size) {
        List<Map<String, Object>> list = sysUserMapper.selectApplicationsByCandidateId(id);

        int total = list.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = fromIndex < total ? list.subList(fromIndex, toIndex) : new ArrayList<>();

        List<ApplicationRecordVO> voList = new ArrayList<>();
        for (Map<String, Object> item : pageList) {
            ApplicationRecordVO vo = new ApplicationRecordVO();
            vo.setId(toLong(item.get("id")));
            vo.setJobTitle((String) item.get("jobTitle"));
            vo.setCompanyName((String) item.get("companyName"));
            vo.setStatus((String) item.get("status"));
            vo.setAppliedAt(toLocalDateTime(item.get("appliedAt")));
            vo.setLastUpdatedAt(toLocalDateTime(item.get("lastUpdatedAt")));
            voList.add(vo);
        }

        return PageResult.of(voList, (long) total, page, size);
    }

    @Override
    public void disableUser(Long userId, String userType) {
        int rows;
        if ("hr".equals(userType) || "interviewer".equals(userType)) {
            // 直接更新 hr_company_member 表
            rows = hrDataMapper.disableUser(userId);
        } else {
            // 直接更新 sys_user 表
            rows = sysUserMapper.updateStatus(userId, "DISABLED");
        }

        if (rows == 0) {
            throw new BusinessException(5201, "用户不存在或已被禁用");
        }

        auditLogService.saveLog(
                AdminSecurityUtil.getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(),
                "DISABLE",
                "USER",
                userId,
                "禁用用户：" + userType,
                AdminSecurityUtil.getIp());
    }

    @Override
    public void enableUser(Long userId, String userType) {
        int rows;
        if ("hr".equals(userType) || "interviewer".equals(userType)) {
            // 直接更新 hr_company_member 表
            rows = hrDataMapper.enableUser(userId);
        } else {
            // 直接更新 sys_user 表
            rows = sysUserMapper.updateStatus(userId, "ACTIVE");
        }

        if (rows == 0) {
            throw new BusinessException(5201, "用户不存在或已是正常状态");
        }

        auditLogService.saveLog(
                AdminSecurityUtil.getCurrentAdminId(),
                AdminSecurityUtil.getCurrentAdminName(),
                "ENABLE",
                "USER",
                userId,
                "启用用户：" + userType,
                AdminSecurityUtil.getIp());
    }

    // ==================== 转换方法 ====================

    private <T> List<EnterpriseGroupVO<T>> groupByEnterprise(List<Map<String, Object>> list,
                                                              java.util.function.Function<Map<String, Object>, T> mapper) {
        Map<Long, EnterpriseGroupVO<T>> map = new LinkedHashMap<>();
        for (Map<String, Object> item : list) {
            Long companyId = ((Number) item.get("companyId")).longValue();
            EnterpriseGroupVO<T> group = map.computeIfAbsent(companyId, k -> {
                EnterpriseGroupVO<T> g = new EnterpriseGroupVO<>();
                g.setCompanyId(k);
                g.setCompanyName((String) item.get("companyName"));
                g.setUsers(new ArrayList<>());
                return g;
            });
            group.getUsers().add(mapper.apply(item));
        }
        return new ArrayList<>(map.values());
    }

    private HRUserVO mapToHRUserVO(Map<String, Object> item) {
        HRUserVO vo = new HRUserVO();
        vo.setId(((Number) item.get("id")).longValue());
        vo.setName(nullToDefault((String) item.get("name"), "未知用户"));
        String phone = (String) item.get("phone");
        vo.setPhone(phone != null && phone.length() == 11
                ? phone.substring(0, 3) + "****" + phone.substring(7) : (phone != null ? phone : "-"));
        vo.setEmail(nullToDefault((String) item.get("email"), "-"));
        vo.setDepartment((String) item.get("department"));
        vo.setStatus((String) item.get("status"));
        return vo;
    }

    private InterviewerVO mapToInterviewerVO(Map<String, Object> item) {
        InterviewerVO vo = new InterviewerVO();
        vo.setId(((Number) item.get("id")).longValue());
        vo.setName(nullToDefault((String) item.get("name"), "未知用户"));
        String phone = (String) item.get("phone");
        vo.setPhone(phone != null && phone.length() == 11
                ? phone.substring(0, 3) + "****" + phone.substring(7) : (phone != null ? phone : "-"));
        vo.setEmail(nullToDefault((String) item.get("email"), "-"));
        vo.setDepartment((String) item.get("department"));
        vo.setInterviewCount(toInt(item.get("interviewCount")));
        vo.setStatus((String) item.get("status"));
        return vo;
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    private Integer toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDateTime) {
            return (java.time.LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        try {
            return java.time.LocalDateTime.parse(value.toString().replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private String formatSalary(Object min, Object max) {
        if (min == null && max == null) return null;
        if (min == null) return max + "元/月";
        if (max == null) return min + "元/月";
        return min + "-" + max + "元/月";
    }
}

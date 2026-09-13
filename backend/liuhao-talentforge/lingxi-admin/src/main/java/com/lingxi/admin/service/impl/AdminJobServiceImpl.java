package com.lingxi.admin.service.impl;

import com.lingxi.admin.domain.dto.OfflineDTO;
import com.lingxi.admin.domain.vo.EnterpriseGroupVO;
import com.lingxi.admin.domain.vo.HeadcountVO;
import com.lingxi.admin.domain.vo.JobDetailVO;
import com.lingxi.admin.domain.vo.JobVO;
import com.lingxi.admin.feign.JobFeignClient;
import com.lingxi.admin.mapper.HrCompanyMapper;
import com.lingxi.admin.mapper.SysUserMapper;
import com.lingxi.admin.service.AdminAuditLogService;
import com.lingxi.admin.service.AdminJobService;
import com.lingxi.admin.util.AdminSecurityUtil;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位管理服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminJobServiceImpl implements AdminJobService {

    private final JobFeignClient jobFeign;
    private final HrCompanyMapper hrCompanyMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminAuditLogService auditLogService;

    @Override
    public List<EnterpriseGroupVO<JobVO>> getJobs() {
        // 调用管理员岗位分页接口，获取所有岗位
        Result<Map<String, Object>> result = jobFeign.getAdminJobs(null, null, null, 1, 1000);
        if (result == null || result.getData() == null) {
            return new ArrayList<>();
        }

        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        // 按企业分组
        Map<Long, EnterpriseGroupVO<JobVO>> groupMap = new LinkedHashMap<>();
        for (Map<String, Object> item : list) {
            Long companyId = toLong(item.get("companyId"));
            EnterpriseGroupVO<JobVO> group = groupMap.computeIfAbsent(companyId, k -> {
                EnterpriseGroupVO<JobVO> g = new EnterpriseGroupVO<>();
                g.setCompanyId(k);
                g.setCompanyName(null);
                g.setUsers(new ArrayList<>());
                return g;
            });

            JobVO vo = new JobVO();
            vo.setId(toLong(item.get("jobId")));
            vo.setTitle((String) item.get("title"));
            vo.setCity((String) item.get("cityName"));
            vo.setStatus((String) item.get("status"));
            vo.setSalary(formatSalary(item));
            vo.setHeadcount(formatHeadcount(item));
            vo.setPublishTime(toLocalDateTime(item.get("publishedAt")));
            group.getUsers().add(vo);
        }

        // 批量查询企业名称回填
        fillCompanyNames(groupMap);

        // 批量查询投递数回填
        fillApplyCounts(list, groupMap);

        return new ArrayList<>(groupMap.values());
    }

    /**
     * 批量回填投递数
     */
    private void fillApplyCounts(List<Map<String, Object>> jobList, Map<Long, EnterpriseGroupVO<JobVO>> groupMap) {
        List<Long> jobIds = jobList.stream()
                .map(item -> toLong(item.get("jobId")))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (jobIds.isEmpty()) return;

        List<Map<String, Object>> counts = sysUserMapper.countApplicationsByJobIds(jobIds);
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : counts) {
            Long jobId = toLong(row.get("jobId"));
            Integer count = toInt(row.get("applyCount"));
            countMap.put(jobId, count);
        }

        for (EnterpriseGroupVO<JobVO> group : groupMap.values()) {
            if (group.getUsers() == null) continue;
            for (JobVO vo : group.getUsers()) {
                vo.setApplyCount(countMap.getOrDefault(vo.getId(), 0));
            }
        }
    }

    /**
     * 批量回填企业名称
     */
    private void fillCompanyNames(Map<Long, EnterpriseGroupVO<JobVO>> groupMap) {
        if (groupMap.isEmpty()) return;
        List<Long> companyIds = new ArrayList<>(groupMap.keySet());
        List<Map<String, Object>> companies = hrCompanyMapper.selectNamesByIds(companyIds);
        if (companies == null || companies.isEmpty()) return;
        Map<Long, String> nameMap = companies.stream()
                .collect(Collectors.toMap(
                        c -> ((Number) c.get("id")).longValue(),
                        c -> (String) c.get("name"),
                        (a, b) -> a));
        for (Map.Entry<Long, EnterpriseGroupVO<JobVO>> entry : groupMap.entrySet()) {
            String name = nameMap.get(entry.getKey());
            if (name != null) {
                entry.getValue().setCompanyName(name);
            }
        }
    }

    private String formatHeadcount(Map<String, Object> item) {
        Integer total = toInt(item.get("totalHc"));
        Integer confirmed = toInt(item.get("confirmedHc"));
        return total + "/" + confirmed;
    }

    @Override
    public JobDetailVO getJobDetail(Long id) {
        Result<Map<String, Object>> result = jobFeign.getJobDetail(id);
        if (result == null || result.getData() == null) {
            throw new BusinessException(5007, "岗位不存在");
        }

        Map<String, Object> data = result.getData();
        JobDetailVO vo = new JobDetailVO();
        vo.setId(toLong(data.get("jobId")));
        vo.setCompanyId(toLong(data.get("companyId")));
        vo.setTitle((String) data.get("title"));
        vo.setStatus((String) data.get("status"));
        vo.setJdSummary((String) data.get("jdSummary"));

        // HC详情
        HeadcountVO hcVo = new HeadcountVO();
        hcVo.setTotal(toInt(data.get("totalHc")));
        hcVo.setConfirmed(toInt(data.get("confirmedHc")));
        hcVo.setReserved(toInt(data.get("reservedHc")));
        hcVo.setAvailable(toInt(data.get("availableHc")));
        vo.setHeadcount(hcVo);

        // 技能标签
        if (data.containsKey("coreSkills") && data.get("coreSkills") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> skills = (List<String>) data.get("coreSkills");
            vo.setSkills(skills);
        }

        return vo;
    }

    @Override
    public void offlineJob(Long jobId, OfflineDTO dto) {
        // 管理员身份由拦截器写入上下文，并透传给岗位服务记录审计日志
        Long operatorId = AdminSecurityUtil.getCurrentAdminId();

        // 先获取岗位详情以获取 version
        Result<Map<String, Object>> detailResult = jobFeign.getJobDetail(jobId);
        if (detailResult == null || detailResult.getData() == null) {
            throw new BusinessException(5007, "岗位不存在");
        }

        Map<String, Object> jobData = detailResult.getData();
        Integer version = toInt(jobData.get("version"));

        // 构建下架请求（使用前端传入的 reason/remark，容错无 body 的调用）
        String reason = (dto != null && dto.getReason() != null) ? dto.getReason() : "VIOLATION";
        String remark = (dto != null && dto.getRemark() != null) ? dto.getRemark() : "管理员违规下架";

        Map<String, Object> request = new HashMap<>();
        request.put("reason", reason);
        request.put("remark", remark);
        request.put("version", version);

        Result<Void> result = jobFeign.offlineJob(jobId, request, operatorId);
        if (result == null || !isSuccess(result)) {
            String errMsg = result != null ? result.getMessage() : "岗位服务暂时不可用";
            throw new BusinessException(503, errMsg);
        }

        auditLogService.saveLog(
                operatorId,
                AdminSecurityUtil.getCurrentAdminName(),
                "OFFLINE",
                "JOB",
                jobId,
                "下架岗位：" + remark,
                AdminSecurityUtil.getIp());
    }

    private boolean isSuccess(Result<?> result) {
        return result.getCode() == 0 || result.getCode() == 200;
    }

    /**
     * 格式化薪资范围（分为单位转为可读字符串，如 "15K-25K"）
     */
    private String formatSalary(Map<String, Object> item) {
        Object negotiableObj = item.get("salaryNegotiable");
        boolean negotiable = negotiableObj instanceof Number && ((Number) negotiableObj).intValue() == 1;
        if (negotiable) {
            return "面议";
        }
        Long min = item.get("salaryMinAmount") instanceof Number
                ? ((Number) item.get("salaryMinAmount")).longValue() : null;
        Long max = item.get("salaryMaxAmount") instanceof Number
                ? ((Number) item.get("salaryMaxAmount")).longValue() : null;
        if (min == null && max == null) {
            return "-";
        }
        if (min == null) {
            return formatAmount(max) + "及以下";
        }
        if (max == null || max.equals(min)) {
            return formatAmount(min);
        }
        return formatAmount(min) + "-" + formatAmount(max);
    }

    /**
     * 分转为K（千元），如 1500000 → "15K"
     */
    private String formatAmount(Long amountFen) {
        if (amountFen == null || amountFen == 0) return "0";
        long yuan = amountFen / 100;
        if (yuan >= 1000 && yuan % 1000 == 0) {
            return (yuan / 1000) + "K";
        }
        return String.format("%.1fK", yuan / 1000.0);
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

    private Long toLong(Object value) {
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    private Integer toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }
}

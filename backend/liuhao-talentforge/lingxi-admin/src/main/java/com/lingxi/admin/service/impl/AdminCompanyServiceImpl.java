package com.lingxi.admin.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.admin.domain.vo.CompanyDetailVO;
import com.lingxi.admin.domain.vo.CompanyMemberVO;
import com.lingxi.admin.domain.vo.CompanyVO;
import com.lingxi.admin.mapper.HrCompanyMapper;
import com.lingxi.admin.mapper.HrDataMapper;
import com.lingxi.admin.service.AdminCompanyService;
import com.lingxi.common.domain.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业管理服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCompanyServiceImpl implements AdminCompanyService {

    private final HrCompanyMapper hrCompanyMapper;
    private final HrDataMapper hrDataMapper;

    @Override
    public PageResult<CompanyVO> getCompanies(String certStatus, String keyword, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<Map<String, Object>> list = hrCompanyMapper.selectByCondition(certStatus, keyword);
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(list);

        // 获取每个企业的岗位数
        List<Map<String, Object>> jobCountList = hrDataMapper.countJobsByCompany();
        Map<Long, Integer> jobCountMap = new java.util.HashMap<>();
        for (Map<String, Object> item : jobCountList) {
            Long companyId = ((Number) item.get("companyId")).longValue();
            Integer count = ((Number) item.get("jobCount")).intValue();
            jobCountMap.put(companyId, count);
        }

        // 获取每个企业的HR数
        List<Map<String, Object>> hrCountList = hrDataMapper.countHRByCompany();
        Map<Long, Integer> hrCountMap = new java.util.HashMap<>();
        for (Map<String, Object> item : hrCountList) {
            Long companyId = ((Number) item.get("companyId")).longValue();
            Integer count = ((Number) item.get("hrCount")).intValue();
            hrCountMap.put(companyId, count);
        }

        List<CompanyVO> voList = list.stream().map(item -> {
            CompanyVO vo = new CompanyVO();
            Long companyId = toLong(item.get("id"));
            vo.setId(companyId);
            vo.setName((String) item.get("name"));
            vo.setIndustry((String) item.get("industry"));
            vo.setScale((String) item.get("scale"));
            vo.setAddress((String) item.get("address"));
            vo.setCertStatus((String) item.get("cert_status"));
            vo.setRegisterTime(toLocalDateTime(item.get("created_at")));
            vo.setJobCount(jobCountMap.getOrDefault(companyId, 0));
            vo.setHrCount(hrCountMap.getOrDefault(companyId, 0));
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    public CompanyDetailVO getCompanyDetail(Long id) {
        Map<String, Object> company = hrCompanyMapper.selectById(id);
        if (company == null) {
            return new CompanyDetailVO();
        }

        CompanyDetailVO vo = new CompanyDetailVO();
        vo.setId(id);
        vo.setName((String) company.get("name"));
        vo.setIndustry((String) company.get("industry"));
        vo.setScale((String) company.get("scale"));
        vo.setAddress((String) company.get("address"));
        vo.setContactPerson((String) company.get("contactPerson"));
        vo.setContactPhone((String) company.get("contactPhone"));
        // 优先用认证表中的营业执照
        String bizLicense = (String) company.get("certBusinessLicenseUrl");
        vo.setBusinessLicenseUrl(bizLicense != null ? bizLicense : (String) company.get("businessLicenseUrl"));
        vo.setCertMaterialUrl((String) company.get("certMaterialUrl"));
        vo.setCertStatus((String) company.get("cert_status"));
        vo.setCertRejectReason((String) company.get("certRejectReason"));
        vo.setCertTime(toLocalDateTime(company.get("certTime")));

        // 获取成员列表
        List<CompanyMemberVO> members = getCompanyMembers(id);
        vo.setMembers(members);
        vo.setJobCount(members.size());
        return vo;
    }

    @Override
    public List<CompanyMemberVO> getCompanyMembers(Long id) {
        // 从HR/面试官列表中过滤该企业的成员
        List<Map<String, Object>> hrList = hrDataMapper.listHRByEnterprise();
        List<Map<String, Object>> interviewerList = hrDataMapper.listInterviewersByEnterprise();

        List<CompanyMemberVO> members = new ArrayList<>();

        for (Map<String, Object> item : hrList) {
            if (id.equals(toLong(item.get("companyId")))) {
                CompanyMemberVO vo = mapMember(item, "HR_ADMIN");
                members.add(vo);
            }
        }

        for (Map<String, Object> item : interviewerList) {
            if (id.equals(toLong(item.get("companyId")))) {
                CompanyMemberVO vo = mapMember(item, "INTERVIEWER");
                vo.setInterviewCount(toInt(item.get("interviewCount")));
                members.add(vo);
            }
        }

        return members;
    }

    private CompanyMemberVO mapMember(Map<String, Object> item, String role) {
        CompanyMemberVO vo = new CompanyMemberVO();
        vo.setUserId(toLong(item.get("id")));
        vo.setName((String) item.get("name"));
        vo.setPhone((String) item.get("phone"));
        vo.setEmail((String) item.get("email"));
        vo.setDepartment((String) item.get("department"));
        vo.setStatus((String) item.get("status"));
        vo.setRole(role);
        vo.setCreatedAt(toLocalDateTime(item.get("createdAt")));
        return vo;
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
        return java.time.LocalDateTime.parse(value.toString().replace(" ", "T"));
    }
}

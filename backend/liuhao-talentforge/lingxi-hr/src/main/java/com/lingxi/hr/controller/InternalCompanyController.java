package com.lingxi.hr.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.entity.HrCompany;
import com.lingxi.hr.mapper.HrCompanyMapper;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业内部接口（供 lingxi-user 等服务调用）
 */
@RestController
@RequestMapping("/internal/companies")
@RequiredArgsConstructor
public class InternalCompanyController {

    private final HrCompanyMapper hrCompanyMapper;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;

    /**
     * 按企业ID查询
     */
    @GetMapping("/{companyId}")
    public Result<Map<String, Object>> getById(@PathVariable Long companyId) {
        HrCompany company = hrCompanyMapper.selectById(companyId);
        if (company == null) {
            return Result.success(null);
        }
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", company.getId());
        vo.put("name", company.getName());
        vo.put("certStatus", company.getCertStatus());
        return Result.success(vo);
    }

    /**
     * 按企业名称查询ID
     */
    @GetMapping("/by-name")
    public Result<Long> getIdByName(@RequestParam String name) {
        Long id = hrCompanyMapper.selectIdByName(name);
        return Result.success(id);
    }

    /**
     * 查询用户所属企业ID
     */
    @GetMapping("/member/company-id")
    public Result<Long> getCompanyIdByUserId(@RequestParam Long userId) {
        Long companyId = hrCompanyMemberMapper.selectCompanyIdByUserId(userId);
        return Result.success(companyId);
    }

    /**
     * 查询所有HR用户ID
     */
    @GetMapping("/members/hr-user-ids")
    public Result<List<Long>> findAllHrUserIds() {
        List<Long> userIds = hrCompanyMemberMapper.findAllHrUserIds();
        return Result.success(userIds);
    }
}

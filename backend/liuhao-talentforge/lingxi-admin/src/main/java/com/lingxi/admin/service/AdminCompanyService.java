package com.lingxi.admin.service;

import com.lingxi.admin.domain.vo.CompanyDetailVO;
import com.lingxi.admin.domain.vo.CompanyMemberVO;
import com.lingxi.admin.domain.vo.CompanyVO;
import com.lingxi.common.domain.PageResult;

import java.util.List;

/**
 * 企业管理服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminCompanyService {

    /**
     * 获取企业列表
     *
     * @param certStatus 认证状态筛选
     * @param keyword    企业名称搜索
     * @param page       页码
     * @param size       每页条数
     * @return 企业列表分页结果
     */
    PageResult<CompanyVO> getCompanies(String certStatus, String keyword, Integer page, Integer size);

    /**
     * 获取企业详情
     *
     * @param id 企业ID
     * @return 企业详情
     */
    CompanyDetailVO getCompanyDetail(Long id);

    /**
     * 获取企业成员列表
     *
     * @param id 企业ID
     * @return 成员列表
     */
    List<CompanyMemberVO> getCompanyMembers(Long id);
}

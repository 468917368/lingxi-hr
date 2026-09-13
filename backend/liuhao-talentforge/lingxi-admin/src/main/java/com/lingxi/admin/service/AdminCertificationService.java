package com.lingxi.admin.service;

import com.lingxi.admin.domain.dto.RejectDTO;
import com.lingxi.admin.domain.vo.CertificationDetailVO;
import com.lingxi.admin.domain.vo.CertificationStatsVO;
import com.lingxi.admin.domain.vo.CertificationVO;
import com.lingxi.common.domain.PageResult;

/**
 * 企业认证审核服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminCertificationService {

    /**
     * 获取审核统计数据
     *
     * @return 审核统计
     */
    CertificationStatsVO getCertificationStats();

    /**
     * 获取审核列表
     *
     * @param status   状态筛选
     * @param keyword  企业名称搜索
     * @param page     页码
     * @param size     每页条数
     * @return 审核列表分页结果
     */
    PageResult<CertificationVO> getCertifications(String status, String keyword, Integer page, Integer size);

    /**
     * 获取审核详情
     *
     * @param id 认证申请ID
     * @return 审核详情
     */
    CertificationDetailVO getCertificationDetail(Long id);

    /**
     * 通过审核
     *
     * @param id 认证申请ID
     */
    void approve(Long id);

    /**
     * 拒绝审核
     *
     * @param id  认证申请ID
     * @param dto 拒绝原因
     */
    void reject(Long id, RejectDTO dto);
}

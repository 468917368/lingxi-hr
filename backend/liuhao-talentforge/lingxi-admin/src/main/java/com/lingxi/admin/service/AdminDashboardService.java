package com.lingxi.admin.service;

import com.lingxi.admin.domain.vo.CompanyRankVO;
import com.lingxi.admin.domain.vo.JobTypeVO;
import com.lingxi.admin.domain.vo.OverviewVO;
import com.lingxi.admin.domain.vo.TrendVO;

import java.util.List;

/**
 * 数据看板服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminDashboardService {

    /**
     * 获取核心指标数据
     *
     * @return 看板概览数据
     */
    OverviewVO getStats();

    /**
     * 获取投递趋势数据（近7天）
     *
     * @return 趋势数据
     */
    TrendVO getTrend();

    /**
     * 获取岗位类型分布
     *
     * @return 岗位类型分布列表
     */
    List<JobTypeVO> getJobDistribution();

    /**
     * 获取企业活跃度排行
     *
     * @return 企业排行列表
     */
    List<CompanyRankVO> getCompanyRank();
}

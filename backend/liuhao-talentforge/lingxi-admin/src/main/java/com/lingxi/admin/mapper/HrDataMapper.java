package com.lingxi.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * HR模块数据Mapper（跨模块直接查数据库）
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Mapper
public interface HrDataMapper {

    // ==================== 企业统计 ====================
    Long countEnterprises();
    Long countTodayEnterprises();

    // ==================== 岗位统计（按企业） ====================
    List<Map<String, Object>> countJobsByCompany();

    // ==================== 面试统计 ====================
    Long countInterviews();
    Long countTodayInterviews();

    // ==================== Offer统计 ====================
    Long countOffers();
    Long countTodayOffers();

    // ==================== HR/面试官列表 ====================
    List<Map<String, Object>> listHRByEnterprise();
    List<Map<String, Object>> listInterviewersByEnterprise();

    // ==================== HR/面试官统计 ====================
    List<Map<String, Object>> countHRByCompany();

    // ==================== 企业排行 ====================
    List<Map<String, Object>> getCompanyRankByConversation();

    // ==================== 禁用/启用（HR/面试官） ====================
    int disableUser(@Param("userId") Long userId);
    int enableUser(@Param("userId") Long userId);
}

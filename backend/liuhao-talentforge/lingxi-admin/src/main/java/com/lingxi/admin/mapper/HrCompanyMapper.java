package com.lingxi.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * hr_company表Mapper（跨模块查询）
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Mapper
public interface HrCompanyMapper {

    /**
     * 按状态统计数量
     */
    Map<String, Object> countByStatus();

    /**
     * 条件查询列表
     */
    List<Map<String, Object>> selectByCondition(@Param("certStatus") String certStatus,
                                                 @Param("keyword") String keyword);

    /**
     * 根据ID查询
     */
    Map<String, Object> selectById(@Param("id") Long id);

    /**
     * 认证审核列表查询（JOIN 认证表 + 联系人）
     */
    List<Map<String, Object>> selectCertifications(@Param("certStatus") String certStatus,
                                                     @Param("keyword") String keyword);

    /**
     * 批量查询企业名称（按ID列表）
     */
    List<Map<String, Object>> selectNamesByIds(@Param("ids") List<Long> ids);

    /**
     * 通过审核
     */
    int approve(@Param("id") Long id);

    /**
     * 拒绝审核
     */
    int reject(@Param("id") Long id, @Param("reason") String reason);
}

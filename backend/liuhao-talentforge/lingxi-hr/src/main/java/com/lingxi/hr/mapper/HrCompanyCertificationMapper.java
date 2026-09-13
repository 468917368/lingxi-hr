package com.lingxi.hr.mapper;

import com.lingxi.hr.domain.entity.HrCompanyCertification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 企业认证申请 Mapper
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Mapper
public interface HrCompanyCertificationMapper {

    /**
     * 插入认证申请（自增主键回填 id）
     */
    int insert(HrCompanyCertification certification);

    /**
     * 按企业ID查询待审核认证申请
     */
    HrCompanyCertification selectPendingByCompanyId(@Param("companyId") Long companyId);
}

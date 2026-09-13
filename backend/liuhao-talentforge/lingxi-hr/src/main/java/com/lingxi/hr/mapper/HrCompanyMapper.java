package com.lingxi.hr.mapper;

import com.lingxi.hr.domain.entity.HrCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 企业信息 Mapper
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Mapper
public interface HrCompanyMapper {

    /**
     * 插入企业（自增主键回填 id）
     */
    int insert(HrCompany company);

    /**
     * 按ID查询企业
     */
    HrCompany selectById(@Param("id") Long id);

    /**
     * 按邀请码查询企业
     */
    HrCompany selectByInviteCode(@Param("inviteCode") String inviteCode);

    /**
     * 按企业名称查询ID
     */
    Long selectIdByName(@Param("name") String name);

    /**
     * 按ID更新企业（动态set，name不更新）
     */
    int updateById(HrCompany company);

    /**
     * 刷新企业邀请码（独立更新，updateById 不包含 invite_code 字段）
     */
    int updateInviteCode(@Param("id") Long id, @Param("inviteCode") String inviteCode);
}

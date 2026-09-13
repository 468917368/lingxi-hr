package com.lingxi.hr.mapper;

import com.lingxi.hr.domain.entity.HrCompanyMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业成员 Mapper
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Mapper
public interface HrCompanyMemberMapper {

    /**
     * 插入成员（自增主键回填 id）
     */
    int insert(HrCompanyMember member);

    /**
     * 按企业ID+用户ID查询成员
     */
    HrCompanyMember selectByCompanyAndUser(@Param("companyId") Long companyId,
                                           @Param("userId") Long userId);

    /**
     * 按企业ID+用户ID查询成员（悲观锁 FOR UPDATE）
     *
     * <p>创建面试时用于串行化同一面试官的并发创建：锁持有至事务提交，
     * 使 {@code countByInterviewerInTimeRange} 时间冲突校验具备原子性（后到者可见先到者已提交记录）。
     */
    HrCompanyMember selectByCompanyAndUserForUpdate(@Param("companyId") Long companyId,
                                                    @Param("userId") Long userId);

    /**
     * 查用户有效的企业成员关系（提交认证前校验用户是否已有企业）
     */
    HrCompanyMember selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 按企业ID查询成员列表（角色/状态可空过滤）
     */
    List<HrCompanyMember> selectByCompanyId(@Param("companyId") Long companyId,
                                            @Param("role") String role,
                                            @Param("status") String status);

    /**
     * 按成员ID查询
     */
    HrCompanyMember selectById(@Param("id") Long id);

    /**
     * 查询用户所属企业ID（状态为ACTIVE）
     */
    Long selectCompanyIdByUserId(@Param("userId") Long userId);

    /**
     * 查询所有HR用户ID
     */
    List<Long> findAllHrUserIds();

    /**
     * 按成员ID硬删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新成员所属部门（个人中心，仅 ACTIVE 成员）
     */
    int updateDepartment(@Param("companyId") Long companyId,
                         @Param("userId") Long userId,
                         @Param("department") String department);
}

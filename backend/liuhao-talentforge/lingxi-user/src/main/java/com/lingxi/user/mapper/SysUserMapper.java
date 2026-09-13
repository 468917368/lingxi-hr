package com.lingxi.user.mapper;

import com.lingxi.user.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户表Mapper
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Mapper
public interface SysUserMapper {

    /**
     * 根据ID查询用户
     */
    SysUser selectById(@Param("id") Long id);

    /**
     * 根据手机号查询用户
     */
    SysUser selectByPhone(@Param("phone") String phone);

    /**
     * 插入用户记录
     */
    int insert(SysUser user);

    /**
     * 更新用户记录
     */
    int updateById(SysUser user);

    /**
     * 获取用户总数
     */
    long countAll();

    /**
     * 获取今日新增用户数
     */
    long countToday();

    /**
     * 分页查询求职者列表
     */
    List<SysUser> selectCandidates(@Param("status") String status,
                                    @Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * 统计求职者总数
     */
    long countCandidates(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 更新用户状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 查询最近N天活跃的求职者
     *
     * @param days 天数
     * @return 用户ID列表
     */
    List<Long> findActiveCandidates(@Param("days") int days);

    /**
     * 查询开启简历公开的求职者
     *
     * @return 用户ID列表
     */
    List<Long> findPublicResumeCandidates();
}

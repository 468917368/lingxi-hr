package com.lingxi.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户表Mapper（跨模块直接查询 sys_user）
 *
 * @author 成员E
 * @since 2026-08-04
 */
@Mapper
public interface SysUserMapper {

    /**
     * 统计用户总数
     */
    long countUsers();

    /**
     * 统计今日新增用户
     */
    long countTodayUsers();

    /**
     * 分页查询求职者列表
     */
    List<Map<String, Object>> selectCandidates(@Param("status") String status,
                                                @Param("keyword") String keyword);

    /**
     * 根据ID查询用户
     */
    Map<String, Object> selectById(@Param("id") Long id);

    /**
     * 查询用户画像
     */
    Map<String, Object> selectProfileByUserId(@Param("userId") Long userId);

    /**
     * 统计简历数
     */
    int countResumesByUserId(@Param("userId") Long userId);

    /**
     * 统计投递数
     */
    int countApplicationsByUserId(@Param("userId") Long userId);

    /**
     * 查询求职者的投递记录列表（JOIN job_post + hr_company）
     */
    List<Map<String, Object>> selectApplicationsByCandidateId(@Param("candidateId") Long candidateId);

    /**
     * 按岗位ID批量统计投递数
     */
    List<Map<String, Object>> countApplicationsByJobIds(@Param("jobIds") List<Long> jobIds);

    /**
     * 更新用户状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}

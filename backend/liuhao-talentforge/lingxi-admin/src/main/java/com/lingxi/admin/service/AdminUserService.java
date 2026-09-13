package com.lingxi.admin.service;

import com.lingxi.admin.domain.vo.*;
import com.lingxi.common.domain.PageResult;

import java.util.List;

/**
 * 用户管理服务
 *
 * @author 成员E
 * @since 2026-08-03
 */
public interface AdminUserService {

    /**
     * 获取HR列表（按企业分组）
     *
     * @return HR列表
     */
    List<EnterpriseGroupVO<HRUserVO>> getHRUsers();

    /**
     * 获取面试官列表（按企业分组）
     *
     * @return 面试官列表
     */
    List<EnterpriseGroupVO<InterviewerVO>> getInterviewers();

    /**
     * 获取求职者列表
     *
     * @param status  状态筛选
     * @param keyword 姓名/手机号搜索
     * @param page    页码
     * @param size    每页条数
     * @return 求职者列表分页结果
     */
    PageResult<CandidateVO> getCandidates(String status, String keyword, Integer page, Integer size);

    /**
     * 获取求职者详情
     *
     * @param id 求职者ID
     * @return 求职者详情
     */
    CandidateDetailVO getCandidateDetail(Long id);

    /**
     * 获取求职者投递记录
     *
     * @param id   求职者ID
     * @param page 页码
     * @param size 每页条数
     * @return 投递记录分页结果
     */
    PageResult<ApplicationRecordVO> getCandidateApplications(Long id, Integer page, Integer size);

    /**
     * 禁用用户
     *
     * @param userId   用户ID
     * @param userType 用户类型（hr/interviewer/candidate）
     */
    void disableUser(Long userId, String userType);

    /**
     * 启用用户
     *
     * @param userId   用户ID
     * @param userType 用户类型（hr/interviewer/candidate）
     */
    void enableUser(Long userId, String userType);
}

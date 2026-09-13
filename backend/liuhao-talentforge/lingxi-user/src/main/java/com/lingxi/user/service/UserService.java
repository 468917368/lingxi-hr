package com.lingxi.user.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.user.domain.dto.UpdatePrivacyRequest;
import com.lingxi.user.domain.dto.UpdateProfileRequest;
import com.lingxi.user.domain.dto.UpdateUserInfoRequest;
import com.lingxi.user.domain.vo.AdminUserDetailVO;
import com.lingxi.user.domain.vo.PrivacyVO;
import com.lingxi.user.domain.vo.UserInfoVO;
import com.lingxi.user.domain.vo.UserProfileVO;
import com.lingxi.user.domain.vo.UserPublicInfoVO;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author 成员A
 * @since 2026-08-01
 */
public interface UserService {

    /**
     * 获取当前用户信息（含画像）
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfoVO getUserInfo(Long userId);

    /**
     * 获取用户公开信息（不含隐私数据）
     *
     * @param userId 用户ID
     * @return 用户公开信息
     */
    UserPublicInfoVO getUserPublicInfo(Long userId);

    /**
     * 更新用户基本信息
     *
     * @param userId  用户ID
     * @param request 更新请求
     */
    void updateUserInfo(Long userId, UpdateUserInfoRequest request);

    /**
     * 更新求职意向
     *
     * @param userId  用户ID
     * @param request 更新请求
     */
    void updateUserProfile(Long userId, UpdateProfileRequest request);

    /**
     * 获取求职者画像（内部接口用）
     *
     * @param userId 用户ID
     * @return 用户画像
     */
    UserProfileVO getUserProfile(Long userId);

    /**
     * 获取隐私设置
     *
     * @param userId 用户ID
     * @return 隐私设置
     */
    PrivacyVO getPrivacySettings(Long userId);

    /**
     * 更新隐私设置
     *
     * @param userId  用户ID
     * @param request 更新请求
     */
    void updatePrivacySettings(Long userId, UpdatePrivacyRequest request);

    /**
     * 更新用户头像URL
     * <p>
     * 负责持久化头像URL到数据库，不负责文件上传/删除。
     * </p>
     *
     * @param userId  用户ID
     * @param avatarUrl 新头像URL
     */
    void updateAvatar(Long userId, String avatarUrl);

    /**
     * 更新用户邮箱（验证码验证通过后调用）
     *
     * @param userId 用户ID
     * @param email  新邮箱地址
     */
    void updateEmail(Long userId, String email);

    /**
     * 修改手机号（验证码验证通过后调用）
     *
     * @param userId  用户ID
     * @param newPhone 新手机号
     */
    void changePhone(Long userId, String newPhone);

    /**
     * 批量获取用户信息（供其他服务调用）
     *
     * @param userIds 用户ID列表
     * @return 用户信息列表
     */
    List<UserInfoVO> getUserInfoByIds(List<Long> userIds);

    /**
     * 获取用户总数
     */
    long getUserCount();

    /**
     * 获取今日新增用户数
     */
    long getTodayUserCount();

    /**
     * 分页获取求职者列表
     */
    PageResult<AdminUserDetailVO> getCandidates(String status, String keyword, Integer page, Integer size);

    /**
     * 获取用户详情（管理端用）
     */
    AdminUserDetailVO getAdminUserDetail(Long userId);

    /**
     * 禁用用户
     */
    void disableUser(Long userId);

    /**
     * 启用用户
     */
    void enableUser(Long userId);
}

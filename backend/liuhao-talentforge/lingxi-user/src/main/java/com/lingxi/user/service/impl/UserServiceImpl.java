package com.lingxi.user.service.impl;

import com.lingxi.common.domain.PageResult;
import com.lingxi.common.enums.UserRole;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.user.domain.dto.UpdatePrivacyRequest;
import com.lingxi.user.domain.dto.UpdateProfileRequest;
import com.lingxi.user.domain.dto.UpdateUserInfoRequest;
import com.lingxi.user.domain.entity.SysUser;
import com.lingxi.user.domain.entity.SysUserProfile;
import com.lingxi.user.domain.vo.AdminUserDetailVO;
import com.lingxi.user.domain.vo.CompanyVO;
import com.lingxi.user.domain.vo.PrivacyVO;
import com.lingxi.user.domain.vo.UserInfoVO;
import com.lingxi.user.domain.vo.UserProfileVO;
import com.lingxi.user.domain.vo.UserPublicInfoVO;
import com.lingxi.user.exception.UserErrorCode;
import com.lingxi.user.feign.HrCompanyFeignClient;
import com.lingxi.user.mapper.SysLoginLogMapper;
import com.lingxi.user.mapper.SysUserMapper;
import com.lingxi.user.mapper.SysUserProfileMapper;
import com.lingxi.user.feign.ResumeFeignClient;
import com.lingxi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户服务实现
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 默认求职状态 */
    private static final String DEFAULT_JOB_STATUS = "JOB_SEEKING";

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper sysUserMapper;
    private final SysUserProfileMapper sysUserProfileMapper;
    private final HrCompanyFeignClient hrCompanyFeignClient;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final ResumeFeignClient resumeFeignClient;

    // ==================== 获取用户信息 ====================

    @Override
    @Cacheable(value = "userInfo", key = "#userId")
    public UserInfoVO getUserInfo(Long userId) {
        return buildUserInfoVO(userId);
    }

    /**
     * 构建UserInfoVO（复用逻辑）
     *
     * @param userId 用户ID
     * @return 用户信息VO
     */
    private UserInfoVO buildUserInfoVO(Long userId) {
        // 1. 查询用户基本信息
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 查询用户画像
        SysUserProfile profile = sysUserProfileMapper.selectByUserId(userId);

        // 3. 构建UserInfoVO（头像URL统一转为相对路径）
        String nameUpdatedAtStr = user.getNameUpdatedAt() != null
                ? user.getNameUpdatedAt().format(DATE_FORMATTER) : null;
        UserInfoVO.UserInfoVOBuilder builder = UserInfoVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .name(user.getName())
                .nameUpdatedAt(nameUpdatedAtStr)
                .role(user.getRole())
                .avatar(toRelativeUrl(user.getAvatar()))
                .email(user.getEmail());

        // 4. 如果有画像，添加到响应中
        if (profile != null) {
            builder.profile(buildUserProfileVO(profile));
        }

        // 5. 如果是HR/面试官，查询企业信息
        if (UserRole.HR.getCode().equals(user.getRole()) || UserRole.INTERVIEWER.getCode().equals(user.getRole())) {
            Long companyId = hrCompanyFeignClient.getCompanyIdByUserId(userId).getData();
            if (companyId != null) {
                builder.companyId(companyId);
                try {
                    Map<String, Object> companyData = hrCompanyFeignClient.getById(companyId).getData();
                    if (companyData != null) {
                        builder.company(CompanyVO.builder()
                                .id(companyId)
                                .name((String) companyData.get("name"))
                                .certStatus((String) companyData.get("certStatus"))
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("查询企业信息失败: companyId={}", companyId, e);
                }
            }
        }

        return builder.build();
    }

    @Override
    public UserPublicInfoVO getUserPublicInfo(Long userId) {
        // 1. 查询用户基本信息
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 查询用户画像
        SysUserProfile profile = sysUserProfileMapper.selectByUserId(userId);

        // 3. 构建公开信息（不含隐私数据）
        UserPublicInfoVO.UserPublicInfoVOBuilder builder = UserPublicInfoVO.builder()
                .id(user.getId())
                .name(user.getName())
                .avatar(toRelativeUrl(user.getAvatar()))
                .role(user.getRole());

        // 4. 如果有画像，添加公开信息
        if (profile != null) {
            builder.workYears(profile.getWorkYears())
                    .education(profile.getEducation())
                    .city(profile.getCity())
                    .jobStatus(profile.getJobStatus())
                    .desiredJob(profile.getDesiredJob())
                    .desiredCity(profile.getDesiredCity())
                    .availableFrom(profile.getAvailableFrom() != null ? profile.getAvailableFrom().toString() : null);
        }

        // 5. 从简历中提取技能标签
        try {
            String skills = extractSkillsFromResume(userId);
            builder.skills(skills);
        } catch (Exception e) {
            log.debug("提取技能标签失败: userId={}", userId);
        }

        return builder.build();
    }

    /**
     * 从简历中提取技能标签
     */
    private String extractSkillsFromResume(Long userId) {
        // 查询默认简历
        com.lingxi.common.domain.Result<Map<String, Object>> result = resumeFeignClient.getUserResume(userId);
        if (result == null || result.getData() == null) {
            return "";
        }

        Map<String, Object> resumeData = result.getData();
        Object cardStructure = resumeData.get("cardStructure");
        if (!(cardStructure instanceof Map)) {
            return "";
        }

        Map<?, ?> card = (Map<?, ?>) cardStructure;
        Object sections = card.get("sections");
        if (!(sections instanceof List)) {
            return "";
        }

        // 查找技能章节
        for (Object section : (List<?>) sections) {
            if (section instanceof Map) {
                String title = (String) ((Map<?, ?>) section).get("title");
                if (title != null && (title.contains("技能") || title.toLowerCase().contains("skill"))) {
                    Object points = ((Map<?, ?>) section).get("points");
                    if (points instanceof List) {
                        List<String> skills = new ArrayList<>();
                        for (Object point : (List<?>) points) {
                            if (point instanceof Map) {
                                String text = (String) ((Map<?, ?>) point).get("text");
                                if (text != null && !text.isEmpty()) {
                                    skills.add(text.trim());
                                }
                            }
                        }
                        return String.join("、", skills);
                    }
                }
            }
        }
        return "";
    }

    // ==================== 更新用户信息 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "userInfo", key = "#userId")
    public void updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 检查姓名修改限制（一个月只能修改一次）
        if (request.getName() != null && !request.getName().equals(user.getName())) {
            if (user.getNameUpdatedAt() != null) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime lastUpdate = user.getNameUpdatedAt();
                // 计算是否满一个月
                if (lastUpdate.plusMonths(1).isAfter(now)) {
                    throw new BusinessException(UserErrorCode.NAME_UPDATE_LIMIT);
                }
            }
        }

        // 3. 更新 sys_user 表（注意：email不在此处更新，需通过邮箱验证流程）
        if (request.getName() != null || request.getAvatar() != null) {
            SysUser updateUser = new SysUser();
            updateUser.setId(userId);
            updateUser.setName(request.getName());
            updateUser.setAvatar(request.getAvatar());
            sysUserMapper.updateById(updateUser);
        }

        // 4. 更新 sys_user_profile 表（基本信息部分）
        SysUserProfile profile = getOrCreateProfile(userId);
        SysUserProfile updateProfile = new SysUserProfile();
        updateProfile.setUserId(userId);
        updateProfile.setGender(request.getGender());
        updateProfile.setCity(request.getCity());
        updateProfile.setWorkYears(request.getWorkYears());
        updateProfile.setEducation(request.getEducation());
        updateProfile.setJobStatus(request.getJobStatus());
        sysUserProfileMapper.updateByUserId(updateProfile);

        log.info("用户信息更新成功: userId={}", userId);
    }

    // ==================== 更新求职意向 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "userInfo", key = "#userId")
    public void updateUserProfile(Long userId, UpdateProfileRequest request) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 查询或创建画像
        SysUserProfile profile = getOrCreateProfile(userId);
        SysUserProfile updateProfile = new SysUserProfile();
        updateProfile.setUserId(userId);
        updateProfile.setDesiredJob(request.getDesiredJob());
        updateProfile.setDesiredCity(request.getDesiredCity());
        updateProfile.setDesiredSalaryMin(request.getDesiredSalaryMin());
        updateProfile.setDesiredSalaryMax(request.getDesiredSalaryMax());
        updateProfile.setAvailableFrom(request.getAvailableFrom());
        sysUserProfileMapper.updateByUserId(updateProfile);

        log.info("求职意向更新成功: userId={}", userId);
    }

    // ==================== 获取画像 ====================

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        SysUserProfile profile = sysUserProfileMapper.selectByUserId(userId);
        if (profile == null) {
            throw new BusinessException(UserErrorCode.USER_PROFILE_NOT_FOUND);
        }
        return buildUserProfileVO(profile);
    }

    // ==================== 隐私设置 ====================

    @Override
    public PrivacyVO getPrivacySettings(Long userId) {
        SysUserProfile profile = sysUserProfileMapper.selectByUserId(userId);
        if (profile == null) {
            // 返回默认隐私设置
            return PrivacyVO.builder()
                    .resumePublic(true)
                    .matchNotify(true)
                    .jobStatus(DEFAULT_JOB_STATUS)
                    .blindMode(false)
                    .build();
        }

        return PrivacyVO.builder()
                .resumePublic(profile.getResumePublic() != null ? profile.getResumePublic() : true)
                .matchNotify(profile.getMatchNotify() != null ? profile.getMatchNotify() : true)
                .jobStatus(profile.getJobStatus() != null ? profile.getJobStatus() : DEFAULT_JOB_STATUS)
                .blindMode(profile.getBlindMode() != null ? profile.getBlindMode() : false)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePrivacySettings(Long userId, UpdatePrivacyRequest request) {
        // 查询或创建画像
        SysUserProfile profile = getOrCreateProfile(userId);
        SysUserProfile updateProfile = new SysUserProfile();
        updateProfile.setUserId(userId);
        updateProfile.setResumePublic(request.getResumePublic());
        updateProfile.setMatchNotify(request.getMatchNotify());
        updateProfile.setJobStatus(request.getJobStatus());
        updateProfile.setBlindMode(request.getBlindMode());
        sysUserProfileMapper.updateByUserId(updateProfile);

        log.info("隐私设置更新成功: userId={}", userId);
    }

    // ==================== 更新头像 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "userInfo", key = "#userId")
    public void updateAvatar(Long userId, String avatarUrl) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 更新头像URL
        SysUser updateUser = new SysUser();
        updateUser.setId(userId);
        updateUser.setAvatar(avatarUrl);
        sysUserMapper.updateById(updateUser);

        log.info("用户头像URL更新成功: userId={}", userId);
    }

    // ==================== 更新邮箱 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "userInfo", key = "#userId")
    public void updateEmail(Long userId, String email) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 更新邮箱
        SysUser updateUser = new SysUser();
        updateUser.setId(userId);
        updateUser.setEmail(email);
        sysUserMapper.updateById(updateUser);

        log.info("用户邮箱更新成功: userId={}", userId);
    }

    // ==================== 修改手机号 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "userInfo", key = "#userId")
    public void changePhone(Long userId, String newPhone) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 检查新手机号是否已注册
        SysUser existingUser = sysUserMapper.selectByPhone(newPhone);
        if (existingUser != null) {
            throw new BusinessException(UserErrorCode.PHONE_ALREADY_REGISTERED);
        }

        // 3. 更新手机号
        SysUser updateUser = new SysUser();
        updateUser.setId(userId);
        updateUser.setPhone(newPhone);
        sysUserMapper.updateById(updateUser);

        log.info("用户手机号修改成功: userId={}", userId);
    }

    // ==================== 批量查询 ====================

    @Override
    public List<UserInfoVO> getUserInfoByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserInfoVO> result = new ArrayList<>();
        for (Long userId : userIds) {
            try {
                UserInfoVO userInfo = buildUserInfoVO(userId);
                result.add(userInfo);
            } catch (Exception e) {
                log.warn("获取用户信息失败: userId={}", userId, e);
                // 跳过失败的用户，不中断批量查询
            }
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 获取或创建用户画像
     *
     * @param userId 用户ID
     * @return 用户画像
     */
    private SysUserProfile getOrCreateProfile(Long userId) {
        SysUserProfile profile = sysUserProfileMapper.selectByUserId(userId);
        if (profile == null) {
            profile = new SysUserProfile();
            profile.setUserId(userId);
            profile.setResumePublic(true);
            profile.setBlindMode(false);
            profile.setMatchNotify(true);
            profile.setJobStatus(DEFAULT_JOB_STATUS);
            sysUserProfileMapper.insert(profile);
        }
        return profile;
    }

    /**
     * 构建UserProfileVO
     *
     * @param profile 用户画像实体
     * @return 用户画像VO
     */
    private UserProfileVO buildUserProfileVO(SysUserProfile profile) {
        return UserProfileVO.builder()
                .gender(profile.getGender())
                .city(profile.getCity())
                .workYears(profile.getWorkYears())
                .education(profile.getEducation())
                .jobStatus(profile.getJobStatus())
                .desiredJob(profile.getDesiredJob())
                .desiredCity(profile.getDesiredCity())
                .desiredSalaryMin(profile.getDesiredSalaryMin())
                .desiredSalaryMax(profile.getDesiredSalaryMax())
                .availableFrom(profile.getAvailableFrom())
                .resumePublic(profile.getResumePublic() != null ? profile.getResumePublic() : true)
                .blindMode(profile.getBlindMode() != null ? profile.getBlindMode() : false)
                .build();
    }

    /**
     * 将URL转换为相对路径
     * <p>
     * 兼容处理：
     * - http://localhost:8086/files/... → /files/...
     * - /files/... → /files/...（不变）
     * - null → null
     * </p>
     *
     * @param url 原始URL
     * @return 相对路径
     */
    private String toRelativeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        // 已经是相对路径
        if (url.startsWith("/files/")) {
            return url;
        }
        // 绝对路径转相对路径
        String absolutePrefix = "http://localhost:8086/files/";
        if (url.startsWith(absolutePrefix)) {
            return "/files/" + url.substring(absolutePrefix.length());
        }
        return url;
    }

    // ==================== 管理端接口（供成员E调用） ====================

    @Override
    public long getUserCount() {
        return sysUserMapper.countAll();
    }

    @Override
    public long getTodayUserCount() {
        return sysUserMapper.countToday();
    }

    @Override
    public PageResult<AdminUserDetailVO> getCandidates(String status, String keyword, Integer page, Integer size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        int offset = (page - 1) * size;
        List<SysUser> users = sysUserMapper.selectCandidates(status, keyword, offset, size);
        long total = sysUserMapper.countCandidates(status, keyword);

        List<AdminUserDetailVO> voList = new ArrayList<>();
        for (SysUser user : users) {
            voList.add(buildAdminUserDetailVO(user));
        }

        return PageResult.of(voList, total, page, size);
    }

    @Override
    public AdminUserDetailVO getAdminUserDetail(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return buildAdminUserDetailVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(404, "用户已被禁用");
        }
        sysUserMapper.updateStatus(userId, "DISABLED");
        log.info("用户已禁用: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        if ("ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(404, "用户已是正常状态");
        }
        sysUserMapper.updateStatus(userId, "ACTIVE");
        log.info("用户已启用: userId={}", userId);
    }

    /**
     * 构建管理端用户详情VO
     */
    private AdminUserDetailVO buildAdminUserDetailVO(SysUser user) {
        AdminUserDetailVO.AdminUserDetailVOBuilder builder = AdminUserDetailVO.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_FORMATTER) : null);

        // 查询最后登录时间
        try {
            LocalDateTime lastLogin = sysLoginLogMapper.selectLastLoginTime(user.getId());
            builder.lastLoginAt(lastLogin != null ? lastLogin.format(DATE_FORMATTER) : null);
        } catch (Exception e) {
            log.debug("查询最后登录时间失败: userId={}", user.getId());
        }

        // 查询画像（求职者才有）
        SysUserProfile profile = sysUserProfileMapper.selectByUserId(user.getId());
        if (profile != null) {
            builder.experience(profile.getWorkYears())
                    .desiredJob(profile.getDesiredJob())
                    .desiredCity(profile.getDesiredCity())
                    .desiredSalaryMin(profile.getDesiredSalaryMin())
                    .desiredSalaryMax(profile.getDesiredSalaryMax());
        }

        return builder.build();
    }
}

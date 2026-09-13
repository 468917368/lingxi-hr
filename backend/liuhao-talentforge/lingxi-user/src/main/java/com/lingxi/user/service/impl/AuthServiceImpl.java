package com.lingxi.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.lingxi.common.constant.RedisKeyConstant;
import com.lingxi.common.exception.AdminLoginErrorCode;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JwtUtil;
import com.lingxi.common.util.RateLimitUtil;
import com.lingxi.common.util.SmsUtil;
import com.lingxi.user.domain.entity.AdminUser;
import com.lingxi.user.domain.entity.SysUser;
import com.lingxi.user.domain.vo.CompanyVO;
import com.lingxi.user.domain.vo.LoginResponse;
import com.lingxi.user.domain.vo.UserInfoVO;
import com.lingxi.user.exception.UserErrorCode;
import com.lingxi.user.feign.HrCompanyFeignClient;
import com.lingxi.user.mapper.AdminUserMapper;
import com.lingxi.user.mapper.SysUserMapper;
import com.lingxi.user.service.AuthService;
import com.lingxi.user.service.LoginLogService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final AdminUserMapper adminUserMapper;
    private final HrCompanyFeignClient hrCompanyFeignClient;
    private final StringRedisTemplate redisTemplate;
    private final SmsUtil smsUtil;
    private final RateLimitUtil rateLimitUtil;
    private final LoginLogService loginLogService;

    /** AccessToken有效期（秒）：2小时 */
    private static final long ACCESS_TOKEN_EXPIRE = 2 * 60 * 60;
    /** RefreshToken有效期（秒）：7天 */
    private static final long REFRESH_TOKEN_EXPIRE = 7 * 24 * 60 * 60;
    /** 登录失败锁定阈值 */
    private static final int LOGIN_FAIL_LOCK_THRESHOLD = 5;
    /** 登录失败锁定时间（分钟） */
    private static final int LOGIN_FAIL_LOCK_MINUTES = 30;
    /** 验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;
    /** 短信发送频率（秒） */
    private static final int SMS_FREQ_SECONDS = 60;
    /** 短信每日上限 */
    private static final int SMS_DAILY_LIMIT = 10;

    // ==================== 发送验证码 ====================

    @Override
    public void sendVerificationCode(String phone, String clientIp) {
        // 1. IP限流（单IP每分钟最多10次）
        String ipRateKey = RedisKeyConstant.format(RedisKeyConstant.IP_RATE_SMS, clientIp);
        if (!rateLimitUtil.isAllowed(ipRateKey, 10, 60)) {
            throw new BusinessException(UserErrorCode.IP_RATE_LIMIT);
        }

        // 2. 检查发送频率
        String freqKey = RedisKeyConstant.format(RedisKeyConstant.SMS_FREQ, phone);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(freqKey))) {
            throw new BusinessException(UserErrorCode.SMS_FREQ_LIMIT);
        }

        // 3. 检查每日上限
        String dailyKey = RedisKeyConstant.format(RedisKeyConstant.SMS_DAILY, phone);
        String dailyCount = redisTemplate.opsForValue().get(dailyKey);
        if (dailyCount != null && Integer.parseInt(dailyCount) >= SMS_DAILY_LIMIT) {
            throw new BusinessException(UserErrorCode.SMS_DAILY_LIMIT);
        }

        // 4. 生成验证码（mock模式固定123456，方便测试）
        String code = smsUtil.isMock() ? "123456" : RandomUtil.randomNumbers(6);

        // 5. 存储验证码
        String codeKey = RedisKeyConstant.format(RedisKeyConstant.SMS_CODE, phone);
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 6. 发送短信
        boolean sent = smsUtil.sendVerificationCode(phone, code);
        if (!sent) {
            throw new BusinessException(UserErrorCode.SMS_SEND_FAILED);
        }

        // 7. 更新频率限制
        redisTemplate.opsForValue().set(freqKey, "1", SMS_FREQ_SECONDS, TimeUnit.SECONDS);

        // 8. 更新每日计数
        redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount == null) {
            redisTemplate.expire(dailyKey, 24, TimeUnit.HOURS);
        }

        log.info("验证码发送成功: phone={}", maskPhone(phone));
    }

    // ==================== 用户注册 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(String phone, String code, String password, String name, String role, String clientIp) {
        // 1. IP限流
        String ipRateKey = RedisKeyConstant.format(RedisKeyConstant.IP_RATE_LOGIN, clientIp);
        if (!rateLimitUtil.isAllowed(ipRateKey, 20, 60)) {
            throw new BusinessException(UserErrorCode.IP_RATE_LIMIT);
        }

        // 2. 校验验证码
        String codeKey = RedisKeyConstant.format(RedisKeyConstant.SMS_CODE, phone);
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null) {
            throw new BusinessException(UserErrorCode.CODE_EXPIRED);
        }
        if (!cachedCode.equals(code)) {
            throw new BusinessException(UserErrorCode.CODE_INVALID);
        }

        // 3. 删除已使用的验证码
        redisTemplate.delete(codeKey);

        // 4. 检查手机号是否已注册
        SysUser existingUser = sysUserMapper.selectByPhone(phone);
        if (existingUser != null) {
            throw new BusinessException(UserErrorCode.PHONE_ALREADY_REGISTERED);
        }

        // 5. 校验角色
        if (!"CANDIDATE".equals(role) && !"HR".equals(role) && !"INTERVIEWER".equals(role)) {
            throw new BusinessException(UserErrorCode.REGISTER_PARAM_INVALID);
        }

        // 6. 创建用户
        SysUser user = new SysUser();
        user.setPhone(phone);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setName(name);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setFirstLogin(0);
        sysUserMapper.insert(user);

        log.info("用户注册成功: userId={}, phone={}, role={}", user.getId(), maskPhone(phone), role);

        // 7. 生成Token并返回
        return generateAndStoreTokens(user);
    }

    // ==================== 验证码登录 ====================

    @Override
    public LoginResponse loginByCode(String phone, String code, String clientIp, String userAgent) {
        // 1. IP限流
        String ipRateKey = RedisKeyConstant.format(RedisKeyConstant.IP_RATE_LOGIN, clientIp);
        if (!rateLimitUtil.isAllowed(ipRateKey, 20, 60)) {
            throw new BusinessException(UserErrorCode.IP_RATE_LIMIT);
        }

        // 2. 检查登录失败锁定（验证码登录独立计数）
        String failKey = "login:fail:code:" + phone;
        Integer failCount = getLoginFailCount(failKey);
        if (failCount >= LOGIN_FAIL_LOCK_THRESHOLD) {
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }

        // 3. 校验验证码
        String codeKey = RedisKeyConstant.format(RedisKeyConstant.SMS_CODE, phone);
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null) {
            loginLogService.recordLog(null, clientIp, userAgent, 0, "验证码已过期");
            throw new BusinessException(UserErrorCode.CODE_EXPIRED);
        }
        if (!cachedCode.equals(code)) {
            incrLoginFail(failKey);
            loginLogService.recordLog(null, clientIp, userAgent, 0, "验证码错误");
            throw new BusinessException(UserErrorCode.CODE_INVALID);
        }

        // 4. 删除已使用的验证码
        redisTemplate.delete(codeKey);

        // 5. 查询用户
        SysUser user = sysUserMapper.selectByPhone(phone);
        if (user == null) {
            loginLogService.recordLog(null, clientIp, userAgent, 0, "用户不存在");
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 6. 检查用户状态
        if ("DISABLED".equals(user.getStatus())) {
            loginLogService.recordLog(user.getId(), clientIp, userAgent, 0, "账号已禁用");
            throw new BusinessException(UserErrorCode.USER_DISABLED);
        }

        // 7. 清除失败计数
        redisTemplate.delete(failKey);

        // 8. 记录登录成功日志
        loginLogService.recordLog(user.getId(), clientIp, userAgent, 1, null);

        // 9. 生成Token并返回
        return generateAndStoreTokens(user);
    }

    // ==================== 密码登录 ====================

    @Override
    public LoginResponse loginByPassword(String phone, String password, String clientIp, String userAgent) {
        // 1. IP限流
        String ipRateKey = RedisKeyConstant.format(RedisKeyConstant.IP_RATE_LOGIN, clientIp);
        if (!rateLimitUtil.isAllowed(ipRateKey, 20, 60)) {
            throw new BusinessException(UserErrorCode.IP_RATE_LIMIT);
        }

        // 2. 检查登录失败锁定（密码登录独立计数）
        String failKey = "login:fail:password:" + phone;
        Integer failCount = getLoginFailCount(failKey);
        if (failCount >= LOGIN_FAIL_LOCK_THRESHOLD) {
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }

        // 3. 查询用户
        SysUser user = sysUserMapper.selectByPhone(phone);
        if (user == null) {
            loginLogService.recordLog(null, clientIp, userAgent, 0, "用户不存在");
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 4. 检查是否已设置密码
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            loginLogService.recordLog(user.getId(), clientIp, userAgent, 0, "未设置密码");
            throw new BusinessException(UserErrorCode.PASSWORD_NOT_SET);
        }

        // 5. 校验密码
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            incrLoginFail(failKey);
            loginLogService.recordLog(user.getId(), clientIp, userAgent, 0, "密码错误");
            throw new BusinessException(UserErrorCode.PASSWORD_ERROR);
        }

        // 6. 检查用户状态
        if ("DISABLED".equals(user.getStatus())) {
            loginLogService.recordLog(user.getId(), clientIp, userAgent, 0, "账号已禁用");
            throw new BusinessException(UserErrorCode.USER_DISABLED);
        }

        // 7. 清除失败计数
        redisTemplate.delete(failKey);

        // 8. 记录登录成功日志
        loginLogService.recordLog(user.getId(), clientIp, userAgent, 1, null);

        // 9. 生成Token并返回
        return generateAndStoreTokens(user);
    }

    // ==================== 刷新Token ====================

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        // 1. 解析RefreshToken（区分过期与无效）
        Claims claims;
        try {
            claims = JwtUtil.parseToken(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(UserErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BusinessException(UserErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 2. 校验Token类型
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new BusinessException(UserErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 3. 获取userId
        Long userId = claims.get("userId", Long.class);

        // 4. 校验Redis中的RefreshToken
        String refreshKey = RedisKeyConstant.format(RedisKeyConstant.USER_REFRESH, userId);
        String storedRefreshToken = redisTemplate.opsForValue().get(refreshKey);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new BusinessException(UserErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 5. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 6. 生成新Token并返回
        return generateAndStoreTokens(user);
    }

    // ==================== 修改密码 ====================

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 2. 检查是否已设置密码
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            throw new BusinessException(UserErrorCode.PASSWORD_NOT_SET);
        }

        // 3. 校验旧密码
        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.PASSWORD_ERROR);
        }

        // 4. 校验新密码不能与旧密码相同
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(UserErrorCode.PASSWORD_SAME_AS_OLD);
        }

        // 5. 更新密码
        user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        sysUserMapper.updateById(user);

        // 6. 清除旧Token
        clearUserTokens(userId);

        log.info("用户修改密码成功: userId={}", userId);
    }

    // ==================== 管理员登录 ====================

    @Override
    public LoginResponse adminLogin(String username, String password, String clientIp, String userAgent) {
        // 1. 检查登录失败锁定
        String failKey = RedisKeyConstant.format(RedisKeyConstant.LOGIN_FAIL, username);
        Integer failCount = getLoginFailCount(failKey);
        if (failCount >= LOGIN_FAIL_LOCK_THRESHOLD) {
            throw new BusinessException(AdminLoginErrorCode.LOGIN_FAILED);
        }

        // 2. 查询管理员（admin_user表）
        AdminUser admin = adminUserMapper.selectByUsername(username);

        // 3. 校验身份
        if (admin == null) {
            incrLoginFail(failKey);
            loginLogService.recordLog(null, clientIp, userAgent, 0, "管理员不存在");
            throw new BusinessException(AdminLoginErrorCode.LOGIN_FAILED);
        }

        // 4. 校验密码
        if (!BCrypt.checkpw(password, admin.getPasswordHash())) {
            incrLoginFail(failKey);
            loginLogService.recordLog(admin.getId(), clientIp, userAgent, 0, "密码错误");
            throw new BusinessException(AdminLoginErrorCode.LOGIN_FAILED);
        }

        // 5. 检查状态
        if ("DISABLED".equals(admin.getStatus())) {
            loginLogService.recordLog(admin.getId(), clientIp, userAgent, 0, "账号已禁用");
            throw new BusinessException(AdminLoginErrorCode.ADMIN_DISABLED);
        }

        // 6. 检查首次登录
        if (admin.getFirstLogin() != null && admin.getFirstLogin() == 1) {
            throw new BusinessException(AdminLoginErrorCode.FIRST_LOGIN);
        }

        // 7. 清除失败计数
        redisTemplate.delete(failKey);

        // 8. 更新最后登录时间
        admin.setLastLoginAt(LocalDateTime.now());
        adminUserMapper.updateById(admin);

        // 9. 生成Token并返回（管理员使用admin_user的id，role=ADMIN）
        String accessToken = JwtUtil.generateAccessToken(admin.getId(), null, "ADMIN", null, null);
        String refreshToken = JwtUtil.generateRefreshToken(admin.getId());

        // 存储Token
        String tokenKey = RedisKeyConstant.format(RedisKeyConstant.USER_TOKEN, admin.getId());
        String refreshKey = RedisKeyConstant.format(RedisKeyConstant.USER_REFRESH, admin.getId());
        redisTemplate.opsForValue().set(tokenKey, accessToken, ACCESS_TOKEN_EXPIRE, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(refreshKey, refreshToken, REFRESH_TOKEN_EXPIRE, TimeUnit.SECONDS);

        loginLogService.recordLog(admin.getId(), clientIp, userAgent, 1, null);
        log.info("管理员登录成功: adminId={}, username={}", admin.getId(), username);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(ACCESS_TOKEN_EXPIRE)
                .user(UserInfoVO.builder()
                        .id(admin.getId())
                        .name(admin.getName())
                        .role("ADMIN")
                        .build())
                .build();
    }

    // ==================== 管理员修改密码 ====================

    @Override
    public void adminChangePassword(Long adminId, String oldPassword, String newPassword) {
        // 1. 查询管理员
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(AdminLoginErrorCode.LOGIN_FAILED);
        }

        // 2. 校验旧密码
        if (!BCrypt.checkpw(oldPassword, admin.getPasswordHash())) {
            throw new BusinessException(AdminLoginErrorCode.LOGIN_FAILED);
        }

        // 3. 校验新密码强度（大小写字母+数字+特殊字符，8-20位）
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,20}$")) {
            throw new BusinessException(AdminLoginErrorCode.PASSWORD_WEAK);
        }

        // 4. 更新密码
        admin.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        admin.setFirstLogin(0);
        adminUserMapper.updateById(admin);

        // 5. 清除旧Token
        clearUserTokens(adminId);

        log.info("管理员修改密码成功: adminId={}", adminId);
    }

    // ==================== 验证码校验 ====================

    @Override
    public void verifyCode(String phone, String code) {
        // 1. 校验验证码
        String codeKey = RedisKeyConstant.format(RedisKeyConstant.SMS_CODE, phone);
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null) {
            throw new BusinessException(UserErrorCode.CODE_EXPIRED);
        }
        if (!cachedCode.equals(code)) {
            throw new BusinessException(UserErrorCode.CODE_INVALID);
        }

        // 2. 删除已使用的验证码
        redisTemplate.delete(codeKey);

        log.info("验证码验证成功: phone={}", maskPhone(phone));
    }

    // ==================== 私有方法 ====================

    /**
     * 生成Token并存储到Redis，构建登录响应
     */
    private LoginResponse generateAndStoreTokens(SysUser user) {
        // 查询HR/面试官的企业信息
        Long companyId = null;
        String certStatus = null;
        CompanyVO companyVO = null;
        if ("HR".equals(user.getRole()) || "INTERVIEWER".equals(user.getRole())) {
            companyId = hrCompanyFeignClient.getCompanyIdByUserId(user.getId()).getData();
            if (companyId != null) {
                try {
                    Map<String, Object> companyData = hrCompanyFeignClient.getById(companyId).getData();
                    if (companyData != null) {
                        companyVO = CompanyVO.builder()
                                .id(companyId)
                                .name((String) companyData.get("name"))
                                .certStatus((String) companyData.get("certStatus"))
                                .build();
                        certStatus = companyVO.getCertStatus();
                    }
                } catch (Exception e) {
                    log.warn("查询企业信息失败: companyId={}", companyId, e);
                }
            }
        }

        String accessToken = JwtUtil.generateAccessToken(
                user.getId(), user.getPhone(), user.getRole(), companyId, certStatus, user.getStatus());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId());

        // 存储Token
        String tokenKey = RedisKeyConstant.format(RedisKeyConstant.USER_TOKEN, user.getId());
        String refreshKey = RedisKeyConstant.format(RedisKeyConstant.USER_REFRESH, user.getId());
        redisTemplate.opsForValue().set(tokenKey, accessToken, ACCESS_TOKEN_EXPIRE, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(refreshKey, refreshToken, REFRESH_TOKEN_EXPIRE, TimeUnit.SECONDS);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(ACCESS_TOKEN_EXPIRE)
                .user(UserInfoVO.builder()
                        .id(user.getId())
                        .phone(user.getPhone())
                        .name(user.getName())
                        .role(user.getRole())
                        .avatar(user.getAvatar())
                        .companyId(companyId)
                        .company(companyVO)
                        .build())
                .build();
    }

    /**
     * 获取登录失败计数，不存在返回0
     */
    private Integer getLoginFailCount(String key) {
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 递增登录失败计数并设置过期时间
     */
    private void incrLoginFail(String key) {
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOGIN_FAIL_LOCK_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 清除用户的所有Token
     */
    private void clearUserTokens(Long userId) {
        String tokenKey = RedisKeyConstant.format(RedisKeyConstant.USER_TOKEN, userId);
        String refreshKey = RedisKeyConstant.format(RedisKeyConstant.USER_REFRESH, userId);
        redisTemplate.delete(tokenKey);
        redisTemplate.delete(refreshKey);
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

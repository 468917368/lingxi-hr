package com.lingxi.user.service;

import com.lingxi.user.domain.dto.ChangePasswordRequest;
import com.lingxi.user.domain.vo.LoginResponse;

/**
 * 认证服务接口
 *
 * @author 成员A
 * @since 2026-08-01
 */
public interface AuthService {

    /**
     * 发送短信验证码
     *
     * @param phone    手机号
     * @param clientIp 客户端IP
     */
    void sendVerificationCode(String phone, String clientIp);

    /**
     * 用户注册（手机号+验证码+密码+姓名+角色）
     *
     * @param phone    手机号
     * @param code     验证码
     * @param password 密码
     * @param name     姓名
     * @param role     角色
     * @param clientIp 客户端IP
     * @return 登录响应（注册后自动登录）
     */
    LoginResponse register(String phone, String code, String password, String name, String role, String clientIp);

    /**
     * 手机号+验证码登录
     *
     * @param phone     手机号
     * @param code      验证码
     * @param clientIp  客户端IP
     * @param userAgent 浏览器UA
     * @return 登录响应
     */
    LoginResponse loginByCode(String phone, String code, String clientIp, String userAgent);

    /**
     * 手机号+密码登录
     *
     * @param phone     手机号
     * @param password  密码
     * @param clientIp  客户端IP
     * @param userAgent 浏览器UA
     * @return 登录响应
     */
    LoginResponse loginByPassword(String phone, String password, String clientIp, String userAgent);

    /**
     * 刷新Token
     *
     * @param refreshToken 刷新Token
     * @return 登录响应
     */
    LoginResponse refreshToken(String refreshToken);

    /**
     * 修改密码（已登录用户）
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 管理员登录
     *
     * @param username  用户名
     * @param password  密码
     * @param clientIp  客户端IP
     * @param userAgent 用户代理
     * @return 登录响应
     */
    LoginResponse adminLogin(String username, String password, String clientIp, String userAgent);

    /**
     * 管理员修改密码（首次登录）
     *
     * @param adminId     管理员ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void adminChangePassword(Long adminId, String oldPassword, String newPassword);

    /**
     * 验证短信验证码（用于修改手机号等场景）
     *
     * @param phone 手机号
     * @param code  验证码
     */
    void verifyCode(String phone, String code);
}

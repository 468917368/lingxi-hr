package com.lingxi.user.service;

/**
 * 邮件服务接口
 *
 * @author 成员A
 * @since 2026-08-02
 */
public interface EmailService {

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱地址
     * @return 验证码（用于存储到Redis）
     */
    String sendEmailVerificationCode(String email);

    /**
     * 验证邮箱验证码
     *
     * @param email 邮箱地址
     * @param code  用户输入的验证码
     * @return true=验证成功
     */
    boolean verifyEmailCode(String email, String code);
}

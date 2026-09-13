package com.lingxi.user.service.impl;

import com.lingxi.common.constant.RedisKeyConstant;
import com.lingxi.common.util.RateLimitUtil;
import com.lingxi.user.exception.UserErrorCode;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * 邮件服务实现
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final RateLimitUtil rateLimitUtil;

    /** SMTP 认证用户（发件人必须与认证账号一致，QQ 邮箱要求 501 校验） */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    /** 验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 10;

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    /** 邮箱验证码Redis Key前缀 */
    private static final String EMAIL_CODE_PREFIX = "email:code:";

    /** 邮箱发送频率Redis Key前缀 */
    private static final String EMAIL_FREQ_PREFIX = "email:freq:";

    @Override
    public String sendEmailVerificationCode(String email) {
        // 1. 检查发送频率（60秒内只能发送一次）
        String freqKey = EMAIL_FREQ_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(freqKey))) {
            throw new BusinessException(UserErrorCode.EMAIL_FREQ_LIMIT);
        }

        // 2. IP限流（单IP每分钟最多5次）
        // 注意：这里需要传入IP，但当前方法没有IP参数，需要在Controller层处理

        // 3. 生成验证码
        String code = generateCode();

        // 4. 存储验证码到Redis
        String codeKey = EMAIL_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 5. 发送邮件
        try {
            sendEmail(email, code);
            log.info("邮箱验证码发送成功: email={}", maskEmail(email));
        } catch (Exception e) {
            log.error("邮箱验证码发送失败: email={}", email, e);
            throw new BusinessException(UserErrorCode.EMAIL_SEND_FAILED);
        }

        // 6. 设置发送频率限制
        redisTemplate.opsForValue().set(freqKey, "1", 60, TimeUnit.SECONDS);

        return code;
    }

    @Override
    public boolean verifyEmailCode(String email, String code) {
        String codeKey = EMAIL_CODE_PREFIX + email;
        String cachedCode = redisTemplate.opsForValue().get(codeKey);

        if (cachedCode == null) {
            throw new BusinessException(UserErrorCode.CODE_EXPIRED);
        }

        if (!cachedCode.equals(code)) {
            throw new BusinessException(UserErrorCode.CODE_INVALID);
        }

        // 验证成功，删除验证码
        redisTemplate.delete(codeKey);
        return true;
    }

    /**
     * 发送邮件
     */
    private void sendEmail(String to, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(resolveFrom());
        helper.setTo(to);
        helper.setSubject("【灵犀招聘】邮箱验证码");
        helper.setText(buildEmailContent(code), true);

        mailSender.send(message);
    }

    /**
     * 发件人地址：优先使用 SMTP 认证账号（spring.mail.username），
     * 否则退回默认地址（避免与认证账号不一致导致 501 Mail from address must be same as authorization user）
     */
    private String resolveFrom() {
        return mailUsername != null && !mailUsername.isEmpty() ? mailUsername : "noreply@lingxi.com";
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(String code) {
        return "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
               "<h2 style='color: #333;'>灵犀招聘 - 邮箱验证</h2>" +
               "<p>您好，</p>" +
               "<p>您的邮箱验证码是：</p>" +
               "<div style='font-size: 24px; font-weight: bold; color: #1890ff; " +
               "background: #f5f5f5; padding: 15px; text-align: center; " +
               "border-radius: 8px; margin: 20px 0;'>" +
               code +
               "</div>" +
               "<p style='color: #999;'>验证码有效期为" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露给他人。</p>" +
               "<p style='color: #999;'>如非本人操作，请忽略此邮件。</p>" +
               "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>" +
               "<p style='color: #999; font-size: 12px;'>灵犀招聘团队</p>" +
               "</div>";
    }

    /**
     * 生成验证码
     *
     * @return 6位数字验证码
     */
    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 邮箱脱敏
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 3) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }
}

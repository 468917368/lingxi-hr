package com.lingxi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 短信工具类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
@Component
public class SmsUtil {

    @Value("${aliyun.sms.mock:true}")
    private boolean mock;

    @Value("${aliyun.sms.sign-name:速通互联验证码}")
    private String signName;

    @Value("${aliyun.sms.template-code:100001}")
    private String templateCode;

    @Resource
    private AliyunSmsHelper aliyunSmsHelper;

    /**
     * 发送验证码短信
     */
    public boolean sendVerificationCode(String phone, String code) {
        if (mock) {
            log.info("【模拟短信】签名: {}, 模板: {}, 手机号: {}, 验证码: {}, 有效期: 3分钟",
                    signName, templateCode, SecurityUtil.maskPhone(phone), code);
            return true;
        }

        String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", code);
        return aliyunSmsHelper.sendSmsVerifyCode(signName, templateCode, phone, templateParam);
    }

    /**
     * 是否为mock模式
     */
    public boolean isMock() {
        return mock;
    }

    /**
     * 校验手机号格式
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }
}

package com.lingxi.common.util;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全工具类（数据脱敏）
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
public class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 手机号脱敏
     * 例：13812345678 -> 138****5678
     */
    public static String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 身份证号脱敏
     * 例：330102199001011234 -> 3301**********1234
     */
    public static String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard) || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 邮箱脱敏
     * 例：zhangsan@example.com -> z***@example.com
     */
    public static String maskEmail(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * 银行卡号脱敏
     * 例：6222021234567890123 -> 6222***********0123
     */
    public static String maskBankCard(String bankCard) {
        if (StrUtil.isBlank(bankCard) || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + "***********" + bankCard.substring(bankCard.length() - 4);
    }

    /**
     * 检查是否包含敏感信息
     */
    public static boolean containsSensitiveInfo(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        // 检查手机号
        if (text.matches(".*1[3-9]\\d{9}.*")) {
            return true;
        }
        // 检查身份证号
        if (text.matches(".*[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx].*")) {
            return true;
        }
        // 检查银行卡号
        if (text.matches(".*[1-9]\\d{15,18}.*")) {
            return true;
        }
        return false;
    }

    /**
     * 检查是否包含Prompt注入关键词
     */
    public static boolean containsPromptInjection(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        String lowerText = text.toLowerCase();
        String[] keywords = {
                "忽略之前的指令", "ignore previous instructions",
                "忽略以上指令", "ignore above instructions",
                "你现在是", "you are now",
                "假装你是", "pretend you are",
                "系统提示", "system prompt"
        };
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

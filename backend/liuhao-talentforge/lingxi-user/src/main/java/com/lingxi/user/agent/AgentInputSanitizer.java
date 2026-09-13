package com.lingxi.user.agent;

import org.springframework.stereotype.Component;

/**
 * Agent 输入安全处理器
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Component
public class AgentInputSanitizer {

    private static final int MAX_LENGTH = 2000;

    private static final String[] INJECTION_PATTERNS = {
            "忽略之前的指令", "忽略上面的指令", "你现在是",
            "ignore previous instructions", "you are now",
            "system prompt", "你的指令是", "忘记你的角色",
            "从现在起你是", "ignore all", "disregard",
            "override", "jailbreak", "DAN mode"
    };

    /**
     * 清洗输入
     *
     * @return 清洗后的文本；如果检测到注入攻击，返回 null
     */
    public String sanitize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String result = input.trim();

        // 长度限制
        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH);
        }

        // Prompt 注入检测
        String lower = result.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                return null;
            }
        }

        // 敏感信息脱敏
        result = maskPhone(result);
        result = maskIdCard(result);

        return result;
    }

    /** 手机号脱敏：138****1234 */
    private String maskPhone(String text) {
        return text.replaceAll("(1[3-9])\\d{4}(\\d{4})", "$1****$2");
    }

    /** 身份证脱敏：3301**********1234 */
    private String maskIdCard(String text) {
        return text.replaceAll("([1-9]\\d{5})\\d{8}(\\d{3}[\\dXx])", "$1********$2");
    }
}

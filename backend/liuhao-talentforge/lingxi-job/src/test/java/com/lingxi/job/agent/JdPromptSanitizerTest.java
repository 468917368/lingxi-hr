package com.lingxi.job.agent;

import com.lingxi.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JD 原文出站前清洗器单测
 * <p>覆盖电话/邮箱/身份证/精确地址替换、超长拒绝、正常文本保留、null 安全。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
class JdPromptSanitizerTest {

    private final JdPromptSanitizer sanitizer = new JdPromptSanitizer();

    @Test
    void phone_masked() {
        assertEquals("联系[已脱敏]", sanitizer.sanitize("联系13812345678"));
    }

    @Test
    void email_masked() {
        assertEquals("邮箱[已脱敏]", sanitizer.sanitize("邮箱test@example.com"));
    }

    @Test
    void idCard_masked() {
        assertEquals("证件[已脱敏]", sanitizer.sanitize("证件110101199003071234"));
    }

    @Test
    void address_masked() {
        // 前缀"地址/坐标"与省名连读（"地址北京"+市）会被地址正则整体吞掉 → 整段替换为 [已脱敏]
        // （宁可多脱敏，保证精确地址到门牌号不出站）
        assertEquals("[已脱敏]", sanitizer.sanitize("地址北京市海淀区中关村大街27号"));
        assertEquals("[已脱敏]", sanitizer.sanitize("坐标深圳市南山区科技园南路123号"));
    }

    @Test
    void tooLong_rejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < JdPromptSanitizer.MAX_JD_LENGTH + 1; i++) {
            sb.append('a');
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sanitizer.sanitize(sb.toString()));
        assertEquals(400, ex.getCode());
    }

    @Test
    void normalText_kept() {
        assertEquals("负责核心后端服务设计与开发", sanitizer.sanitize("负责核心后端服务设计与开发"));
    }

    @Test
    void nullSafe() {
        assertNull(sanitizer.sanitize(null));
    }
}

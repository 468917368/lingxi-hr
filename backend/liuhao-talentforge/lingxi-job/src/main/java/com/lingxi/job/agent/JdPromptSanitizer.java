package com.lingxi.job.agent;

import com.lingxi.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * JD 原文出站前清洗器（阶段4 真实协议修订）
 * <p>
 * JD 原文在发给百宝箱前必须执行本清洗：
 * <ol>
 *   <li>长度限长：{@code > 20000} 拒绝（400，与 {@code JdParseRequest @Size} 一致）</li>
 *   <li>敏感片段替换：手机号/邮箱/身份证号/精确地址（到门牌号）→ {@code [已脱敏]}</li>
 * </ol>
 * 禁止记录清洗前后正文；模型 Prompt 只接收清洗后的 JD。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Component
public class JdPromptSanitizer {

    /** JD 最大长度（字符） */
    public static final int MAX_JD_LENGTH = 20000;

    /** 手机号 */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    /** 邮箱 */
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    /** 身份证号（18 位） */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    /** 精确地址（省市/区县 + 道路 + 门牌号） */
    private static final Pattern ADDRESS = Pattern.compile(
            "[\\u4e00-\\u9fff]{2,10}(省|市)[\\u4e00-\\u9fff]{2,10}(市|区|县)"
                    + "[\\u4e00-\\u9fff]{1,12}(路|街|大道|大街)[0-9]{1,6}(号|栋)?");

    /** 脱敏占位符 */
    private static final String MASK = "[已脱敏]";

    /**
     * 清洗 JD 原文：限长 + 敏感片段替换
     *
     * @param jdText JD 原文（Controller 层已 @Valid，此处为二次兜底）
     * @return 清洗后的 JD（可安全出站）
     * @throws BusinessException 超长 → 400
     */
    public String sanitize(String jdText) {
        if (jdText == null) {
            return null;
        }
        if (jdText.length() > MAX_JD_LENGTH) {
            log.warn("JD 文本超长（{} > {}），拒绝出站", jdText.length(), MAX_JD_LENGTH);
            throw new BusinessException(400, "JD文本长度不能超过20000");
        }
        String s = jdText;
        s = maskSensitiveFields(s);
        s = ADDRESS.matcher(s).replaceAll(MASK);
        return s;
    }

    /**
     * 敏感字段脱敏（身份证 → 手机号 → 邮箱），供面试出题侧简历脱敏复用
     * <p>顺序敏感：身份证（18 位）必须先于手机号替换，否则身份证中间的 {@code 1[3-9]\d{9}} 片段
     * 会被误判为手机号。null 安全。</p>
     */
    public static String maskSensitiveFields(String text) {
        if (text == null) {
            return null;
        }
        String s = text;
        s = ID_CARD.matcher(s).replaceAll(MASK);
        s = PHONE.matcher(s).replaceAll(MASK);
        s = EMAIL.matcher(s).replaceAll(MASK);
        return s;
    }
}

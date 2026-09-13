package com.lingxi.job.validator;

import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.response.JobRequirementResponse.CoreSkill;
import com.lingxi.job.domain.dto.response.JobRequirementResponse.SoftSkill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能标签输入清洗器单元测试（纯单测，直接 new，不涉及 Spring/HTTP）
 * <p>断言仅验证 BusinessException 的 code/message；HTTP 语义（HTTP 200 + code=400）由
 * HrJobControllerTest 接口级用例覆盖。</p>
 *
 * @author 成员B
 * @since 2026-08-10
 */
class JobProfileInputSanitizerTest {

    private final JobProfileInputSanitizer sanitizer = new JobProfileInputSanitizer();

    // ==================== 合法标签（保留/清洗） ====================

    /** 合法特殊字符（+ # . / - 等）不得被误伤 */
    @Test
    void legalSpecialChars_preserved() {
        List<String> legal = Arrays.asList("C++", "C#", ".NET", "Node.js", "CI/CD", "Spring Boot");
        for (String name : legal) {
            List<CoreSkill> result = sanitizer.sanitize(skills(name));
            assertEquals(1, result.size(), "合法标签应通过: " + name);
            assertEquals(name, result.get(0).getName(), "合法标签应原样保留: " + name);
        }
    }

    /** 防误杀：含广告词子串的合法技术标签必须通过 */
    @Test
    void legalLabels_withAdSubstrings_pass() {
        List<String> legal = Arrays.asList("微信小程序", "QQ开放平台", "扫码支付", "扫码进入",
                "二维码识别", "二维码扫描", "扫码注册流程", "返利系统", "返利业务", "代理模式",
                "微信聊天机器人", "vxworks", "vxlan");
        for (String name : legal) {
            List<CoreSkill> result = sanitizer.sanitize(skills(name));
            assertEquals(1, result.size(), "合法标签不应被误杀: " + name);
            assertEquals(name, result.get(0).getName(), "合法标签应原样保留: " + name);
        }
    }

    /** trim + 控制字符清洗：首尾空白与换行/Tab 被剔除 */
    @Test
    void trimAndControlChars_cleaned() {
        List<CoreSkill> result = sanitizer.sanitize(skills("  Java\t\n "));
        assertEquals("Java", result.get(0).getName(), "应清洗为 Java");
    }

    /** 忽略大小写去重：保留首次出现，保序，保存原始大小写 */
    @Test
    void dedup_ignoreCase_keepFirst() {
        List<CoreSkill> result = sanitizer.sanitize(skills("Java", "java", "JAVA", "Spring"));
        assertEquals(2, result.size(), "忽略大小写去重后应剩 2 项");
        assertEquals("Java", result.get(0).getName(), "保留首次出现的原始大小写");
        assertEquals("Spring", result.get(1).getName(), "保序");
    }

    // ==================== 清洗拦截（空/超长/数量） ====================

    /** 空标签（纯空白）→ 400 */
    @Test
    void blankTag_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sanitizer.sanitize(skills("   ")));
        assertBusiness400(ex, JobProfileInputSanitizer.EMPTY_MESSAGE);
    }

    /** null name（空对象 {} 反序列化场景）→ 400 */
    @Test
    void nullName_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sanitizer.sanitize(skills(null, "Java")));
        assertBusiness400(ex, JobProfileInputSanitizer.EMPTY_MESSAGE);
    }

    /** 列表项为 null（"coreSkills":[null]）→ 400，不出现 NPE/500 */
    @Test
    void nullCoreSkillItem_rejected() {
        List<CoreSkill> skills = new ArrayList<>();
        skills.add(null);
        skills.add(skill("Java"));
        BusinessException ex = assertThrows(BusinessException.class, () -> sanitizer.sanitize(skills));
        assertBusiness400(ex, JobProfileInputSanitizer.EMPTY_MESSAGE);
    }

    /** 列表项为 null（softSkills）→ 400，不出现 NPE/500 */
    @Test
    void nullSoftSkillItem_rejected() {
        List<SoftSkill> skills = new ArrayList<>();
        skills.add(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> sanitizer.sanitizeSoftSkills(skills));
        assertBusiness400(ex, JobProfileInputSanitizer.EMPTY_MESSAGE);
    }

    /** 超长（>64 字符）→ 400 */
    @Test
    void tooLong_rejected() {
        String longName = String.join("", Collections.nCopies(65, "a"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sanitizer.sanitize(skills(longName)));
        assertBusiness400(ex, JobProfileInputSanitizer.TOO_LONG_MESSAGE);
    }

    /** 64 字符边界：恰好 64 通过 */
    @Test
    void maxLengthBoundary_pass() {
        String name = String.join("", Collections.nCopies(64, "a"));
        assertEquals(1, sanitizer.sanitize(skills(name)).size(), "恰好 64 字符应通过");
    }

    /** 10 项防御兜底：11 项（含重复）在去重前拦截 → 400 */
    @Test
    void moreThan10Items_rejected() {
        List<CoreSkill> skills = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            skills.add(skill("Java"));
        }
        BusinessException ex = assertThrows(BusinessException.class, () -> sanitizer.sanitize(skills));
        assertBusiness400(ex, JobProfileInputSanitizer.TOO_MANY_MESSAGE);
    }

    // ==================== 广告拦截（强特征 + 组合特征 + 绕过形态） ====================

    /** 广告强特征：手机号 */
    @Test
    void phone_rejected() {
        assertAdRejected("13800000001");
    }

    /** 广告强特征：邮箱 */
    @Test
    void email_rejected() {
        assertAdRejected("contact@example.com");
    }

    /** 广告强特征：URL（http/https/www） */
    @Test
    void url_rejected() {
        assertAdRejected("http://ad.com");
        assertAdRejected("https://ad.com");
        assertAdRejected("www.ad.com");
    }

    /** 广告强特征：日赚/兼职刷单 */
    @Test
    void strongChineseKeywords_rejected() {
        assertAdRejected("日赚500");
        assertAdRejected("兼职刷单");
    }

    /** 广告组合特征：微信类 */
    @Test
    void wechatCombinations_rejected() {
        assertAdRejected("加微信xxx");
        assertAdRejected("微信号abc");
        assertAdRejected("微信同号");
        assertAdRejected("联系微信");
        assertAdRejected("微信联系");
    }

    /** 微信插入变体与缩写（2026-08-10 修复）：加我微信/加个微信/加V/vx/v信 必须拦截 */
    @Test
    void wechatVariants_rejected() {
        assertAdRejected("加我微信");
        assertAdRejected("加个微信");
        assertAdRejected("加下微信");
        assertAdRejected("微信加我");
        assertAdRejected("微信加个");
        assertAdRejected("加V");
        assertAdRejected("加vx");
        assertAdRejected("vx号123");
        assertAdRejected("加v信");
        assertAdRejected("v信");
        assertAdRejected("微信私聊");
        assertAdRejected("留微信");
    }

    /** QQ 插入变体：加我QQ/加个QQ 必须拦截 */
    @Test
    void qqVariants_rejected() {
        assertAdRejected("加我QQ");
        assertAdRejected("加个QQ");
        assertAdRejected("QQ加我");
    }

    /** 广告组合特征：QQ 类 */
    @Test
    void qqCombinations_rejected() {
        assertAdRejected("QQ号123");
        assertAdRejected("加QQ");
        assertAdRejected("QQ群领资料");
        assertAdRejected("联系QQ");
    }

    /** 广告组合特征：扫码/二维码/代理/返利类 */
    @Test
    void otherCombinations_rejected() {
        assertAdRejected("扫码加群");
        assertAdRejected("扫码联系");
        assertAdRejected("二维码加群");
        assertAdRejected("诚招代理");
        assertAdRejected("招代理");
        assertAdRejected("高额返利");
        assertAdRejected("返利赚钱");
    }

    /** NFKC 绕过：全角 ＱＱ号123 → QQ号123 → 命中 QQ号 组合特征 */
    @Test
    void fullWidthBypass_rejected() {
        assertAdRejected("ＱＱ号123");
    }

    /** 零宽字符绕过：加微​信 → 移除 Cf → 加微信 → 命中组合特征 */
    @Test
    void zeroWidthBypass_rejected() {
        assertAdRejected("加微​信");
    }

    // ==================== softSkills 同步清洗 ====================

    /** softSkills：广告 → 400 */
    @Test
    void softSkills_adRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sanitizer.sanitizeSoftSkills(softSkills("加微信123")));
        assertBusiness400(ex, JobProfileInputSanitizer.AD_MESSAGE);
    }

    /** softSkills：合法通过 + 清洗生效 */
    @Test
    void softSkills_legalPass() {
        List<SoftSkill> result = sanitizer.sanitizeSoftSkills(softSkills(" 沟通能力 ", "团队协作"));
        assertEquals(2, result.size());
        assertEquals("沟通能力", result.get(0).getName(), "应 trim 清洗");
    }

    // ==================== 辅助方法 ====================

    /** 广告断言统一入口 */
    private void assertAdRejected(String name) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sanitizer.sanitize(skills(name)));
        assertBusiness400(ex, JobProfileInputSanitizer.AD_MESSAGE);
    }

    /** 断言 BusinessException code=400 且 message 匹配 */
    private void assertBusiness400(BusinessException ex, String message) {
        assertEquals(400, ex.getCode(), "业务码应为 400，实际: " + ex.getCode());
        assertTrue(ex.getMessage().contains(message),
                "消息应包含「" + message + "」，实际: " + ex.getMessage());
    }

    /** 构造 CoreSkill 列表（name 列表 → 默认 level/required） */
    private List<CoreSkill> skills(String... names) {
        List<CoreSkill> list = new ArrayList<>();
        for (String name : names) {
            list.add(skill(name));
        }
        return list;
    }

    private CoreSkill skill(String name) {
        CoreSkill s = new CoreSkill();
        s.setName(name);
        s.setLevel("3");
        s.setRequired(true);
        return s;
    }

    private List<SoftSkill> softSkills(String... names) {
        List<SoftSkill> list = new ArrayList<>();
        for (String name : names) {
            SoftSkill s = new SoftSkill();
            s.setName(name);
            s.setImportance("3");
            list.add(s);
        }
        return list;
    }
}

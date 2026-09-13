package com.lingxi.job.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 公共题目脱敏器单元测试（阶段6.3 二期）
 * <p>验证候选人姓名/公司/项目/学校等敏感信息在入库前被替换，纯文本不受影响。</p>
 */
public class QuestionContentSanitizerTest {

    private final QuestionContentSanitizer sanitizer = new QuestionContentSanitizer();

    @Test
    public void nullSafe() {
        Assertions.assertNull(sanitizer.sanitize(null));
    }

    @Test
    public void masksContactAndSensitiveEntities() {
        String in = "张伟同学 13812345678 test@xx.com 110101199001011234 "
                + "腾讯科技有限公司 智慧城市项目 北京大学 微信号 wx_abc";
        String out = sanitizer.sanitize(in);
        // 联系方式
        Assertions.assertFalse(out.contains("13812345678"), "手机号应脱敏");
        Assertions.assertFalse(out.contains("test@xx.com"), "邮箱应脱敏");
        Assertions.assertFalse(out.contains("110101199001011234"), "身份证应脱敏");
        Assertions.assertFalse(out.contains("wx_abc"), "微信号应脱敏");
        // 实体信息
        Assertions.assertFalse(out.contains("张伟"), "候选人姓名应脱敏");
        Assertions.assertFalse(out.contains("腾讯科技有限公司"), "公司应脱敏");
        Assertions.assertFalse(out.contains("智慧城市项目"), "项目应脱敏");
        Assertions.assertFalse(out.contains("北京大学"), "学校应脱敏");
        // 脱敏占位符
        Assertions.assertTrue(out.contains("候选人"), "姓名应替换为候选人");
        Assertions.assertTrue(out.contains("某公司"), "公司应替换为某公司");
        Assertions.assertTrue(out.contains("某项目"), "项目应替换为某项目");
        Assertions.assertTrue(out.contains("某大学"), "学校应替换为某大学");
    }

    @Test
    public void plainTextUnchanged() {
        String in = "请解释 HashMap 扩容机制及其触发条件";
        Assertions.assertEquals(in, sanitizer.sanitize(in));
    }
}

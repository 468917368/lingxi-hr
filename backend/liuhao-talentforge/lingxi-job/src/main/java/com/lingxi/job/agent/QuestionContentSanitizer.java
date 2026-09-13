package com.lingxi.job.agent;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * AI 题目内容二次脱敏器（阶段6.3 二期，抽取自 InterviewAgentServiceImpl.sanitize，规则保持一致）
 * <p>面试官申请入库前必须执行：确保候选人姓名、公司、项目、学校、联系方式等不沉淀进企业题库。
 * 复用 {@link JdPromptSanitizer#maskSensitiveFields}（身份证先于手机号顺序约束），避免双处维护。</p>
 *
 * @author 成员B
 * @since 2026-08-06
 */
@Component
public class QuestionContentSanitizer {

    /** URL（http/https） */
    private static final Pattern URL = Pattern.compile("https?://\\S+");

    /** 微信号/WeChat 及账号（保留 wechat/微信 前缀，脱敏账号部分） */
    private static final Pattern WECHAT = Pattern.compile("(?i)(wechat|微信|微信号?)\\s*[:：]?\\s*[\\w-]+");

    /**
     * 实体后缀词集合（用于实体名前缀逐字符排除）。
     * <p>⚠️ 修复继承自 InterviewAgentServiceImpl.sanitize 的贪婪缺陷：
     * 原正则 {@code [一-龥]{2,N}(项目)} 在连续中文句（如「负责智慧城市项目」）会从句子开头贪婪吞掉整段。
     * 本实现让前缀在遇到任一实体后缀词时停止，使各实体就近独立脱敏；并额外排除「司」，
     * 防止项目正则从「某公司」的后半字「司」穿行（否则公司名只脱一半会残留「腾讯科技有限」）。
     * 注意：本表声明的集团/研究院/学院无对应替换规则，属已知启发式边界（见遗留清单 #37）。</p>
     */
    private static final String ENTITY_SUFFIX = "公司|项目|大学|学校|机构|学院|集团|研究院|司";

    /**
     * 脱敏 6 步兜底：手机号/邮箱/身份证/URL/微信号 → [已脱敏]；
     * 姓名→候选人、公司→某公司、项目→某项目、学校/机构/大学→某+后缀。null 安全。
     * <p>正则启发式，非完全脱敏（裸公司名/无后缀姓名等漏网属已知边界，见遗留清单 #37）。</p>
     */
    public String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String s = JdPromptSanitizer.maskSensitiveFields(text);
        s = URL.matcher(s).replaceAll("[已脱敏]");
        s = WECHAT.matcher(s).replaceAll("$1[已脱敏]");
        s = s.replaceAll("([\\u4e00-\\u9fff]{2,6})(?=的?简历|先生|女士|同学)", "候选人");
        // 实体名：前缀逐字符排除其他实体后缀词，就近独立替换（见 ENTITY_SUFFIX 说明）
        // ⚠️ 公司必须最先执行：先把「XX有限公司/XX公司」整体换成占位，再处理项目/大学等，
        //   否则项目正则会在「XX公司 负责 XX项目」中把「司」与动词一并吞掉，残留半截公司名。
        s = s.replaceAll("(?:(?!" + ENTITY_SUFFIX + ")[\\u4e00-\\u9fff]){2,8}(有限公司|公司)", "某公司");
        s = s.replaceAll("(?:(?!" + ENTITY_SUFFIX + ")[\\u4e00-\\u9fff]){2,8}?(项目)", "某项目");
        s = s.replaceAll("(?:(?!" + ENTITY_SUFFIX + ")[\\u4e00-\\u9fff]){2,8}?(大学)", "某大学");
        s = s.replaceAll("(?:(?!" + ENTITY_SUFFIX + ")[\\u4e00-\\u9fff]){2,8}?(学校)", "某学校");
        s = s.replaceAll("(?:(?!" + ENTITY_SUFFIX + ")[\\u4e00-\\u9fff]){2,8}?(机构)", "某机构");
        return s;
    }
}

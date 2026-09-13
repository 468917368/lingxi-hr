package com.lingxi.job.validator;

import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import com.lingxi.job.domain.dto.response.JobRequirementResponse.CoreSkill;
import com.lingxi.job.domain.dto.response.JobRequirementResponse.SoftSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 岗位画像技能标签输入清洗器（广告/垃圾内容检测）
 * <p>
 * coreSkills/softSkills 的 name 字段在创建/更新岗位时统一经本器清洗：
 * NFKC 规范化 → 移除 Cc/Cf 控制字符 → trim → 空拒绝 → 限长 1~64 → 广告特征检测（命中 400）→
 * 忽略大小写去重（保序，保留首次出现）。命中广告直接拒绝，不做脱敏替换保存。
 * </p>
 * <p>
 * 广告特征分两层：强特征（URL/手机号/邮箱/日赚/兼职刷单，独立词命中即拦）与
 * 组合特征（加微信/QQ号/扫码加群/诚招代理/高额返利 等明显导流组合），避免误杀
 * 「微信小程序」「扫码支付」「代理模式」等合法技术标签。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-10
 */
@Slf4j
@Component
public class JobProfileInputSanitizer {

    /** 技能标签最大条数（防御性兜底；原始请求 ≤10 已由 DTO @Size 在 Controller 层拦截） */
    public static final int MAX_SKILLS = 10;

    /** 单项最大长度（字符） */
    public static final int MAX_SKILL_LENGTH = 64;

    /** 广告命中统一提示 */
    public static final String AD_MESSAGE = "技能标签包含联系方式或广告内容，请修改后重试";

    /** 空标签统一提示 */
    public static final String EMPTY_MESSAGE = "技能标签不能为空";

    /** 超长统一提示 */
    public static final String TOO_LONG_MESSAGE = "技能标签长度不能超过" + MAX_SKILL_LENGTH + "字符";

    /** 数量超限统一提示 */
    public static final String TOO_MANY_MESSAGE = "技能标签最多" + MAX_SKILLS + "项";

    /** 手机号（与 JdPromptSanitizer 保持同一正则，改动需同步） */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    /** 邮箱（与 JdPromptSanitizer 保持同一正则，改动需同步） */
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** URL（http/https/www. 前缀） */
    private static final Pattern URL = Pattern.compile("https?://|www\\.", Pattern.CASE_INSENSITIVE);

    /**
     * 广告关键词（组合特征 + 强特征中文词，忽略大小写）
     * 仅保留明显导流组合：微信/QQ/扫码/二维码/代理/返利 均为"动作+主体"组合，
     * 避免误杀「微信小程序」「扫码支付」「二维码识别」「代理模式」「返利系统」等合法标签；
     * 「日赚」「兼职刷单」为强特征独立词（无合法技术含义）。
     * <p>2026-08-10 修复：微信/QQ 组合支持插入变体（加我微信/加个微信/微信加我）与
     * 拼音缩写（加V/加vx/v信），防「加我微信」类标签绕过；CASE_INSENSITIVE 下 v/V 统一。</p>
     */
    private static final Pattern AD_KEYWORDS = Pattern.compile(
            "加[我个下]?微信|微信加[我个下]?|微信号|微信联系|微信同号|联系微信"
                    + "|微信私聊|微信详聊|微信详谈|留微信|要微信"
                    + "|加V|加vx|vx号|加v信|v信"
                    + "|加[我个下]?QQ|QQ加[我个下]?|QQ号|QQ群|QQ咨询|联系QQ"
                    + "|扫码联系|扫码加群|扫码添加"
                    + "|二维码加群|二维码联系"
                    + "|诚招代理|代理加盟|招代理|代理招募|代理合作"
                    + "|高额返利|返利赚钱"
                    + "|日赚|兼职刷单",
            Pattern.CASE_INSENSITIVE);

    /**
     * 清洗+校验核心技能标签（原地替换 DTO 列表）
     *
     * @param skills 核心技能列表（null/空直接返回，DRAFT 允许无技能）
     * @return 清洗后的列表（trim/去控制字符/去重后）
     */
    public List<CoreSkill> sanitize(List<CoreSkill> skills) {
        return sanitizeNames(skills, CoreSkill::getName, CoreSkill::setName);
    }

    /**
     * 清洗+校验软能力标签（原地替换 DTO 列表）
     *
     * @param skills 软能力列表（null/空直接返回）
     * @return 清洗后的列表
     */
    public List<SoftSkill> sanitizeSoftSkills(List<SoftSkill> skills) {
        return sanitizeNames(skills, SoftSkill::getName, SoftSkill::setName);
    }

    /**
     * 通用技能名清洗（两种 DTO 复用同一逻辑）
     * <p>流程：10 项防御兜底（去重前）→ 逐项：列表元素判空 → name 判空 →
     * NFKC → 去 Cc/Cf → trim → 空拒绝 → 限长 → 广告检测 → 忽略大小写去重（保序）。</p>
     */
    private <T> List<T> sanitizeNames(List<T> items, Function<T, String> nameGetter,
                                      BiConsumer<T, String> nameSetter) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        // 10 项防御兜底必须在去重前检查：若去重后检查，11 个重复标签会被去重成 1 个而放行
        if (items.size() > MAX_SKILLS) {
            throw new BusinessException(400, TOO_MANY_MESSAGE);
        }
        List<T> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (T item : items) {
            // 列表元素判空（防 "coreSkills":[null] 触发 NPE→500）
            if (item == null) {
                throw new BusinessException(400, EMPTY_MESSAGE);
            }
            String name = nameGetter.apply(item);
            if (name == null) {
                throw new BusinessException(400, EMPTY_MESSAGE);
            }
            // NFKC 规范化（全角→半角，防 ＱＱ 绕过）→ 移除 Cc/Cf 控制字符（防零宽字符/换行绕过）→ trim
            String normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
                    .replaceAll("[\\p{Cc}\\p{Cf}]", "")
                    .trim();
            if (normalized.isEmpty()) {
                throw new BusinessException(400, EMPTY_MESSAGE);
            }
            if (normalized.length() > MAX_SKILL_LENGTH) {
                throw new BusinessException(400, TOO_LONG_MESSAGE);
            }
            if (isAd(normalized)) {
                throw new BusinessException(400, AD_MESSAGE);
            }
            // 忽略大小写去重（Locale.ROOT 防 locale 敏感），保序保留首次出现，保存原始大小写
            String key = normalized.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                nameSetter.accept(item, normalized);
                result.add(item);
            }
        }
        return result;
    }

    /** 广告特征检测（清洗后执行，命中任一特征即拒绝） */
    private boolean isAd(String s) {
        return URL.matcher(s).find()
                || PHONE.matcher(s).find()
                || EMAIL.matcher(s).find()
                || AD_KEYWORDS.matcher(s).find();
    }
}

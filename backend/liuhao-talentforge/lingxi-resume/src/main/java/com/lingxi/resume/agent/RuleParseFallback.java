package com.lingxi.resume.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 降级规则解析引擎
 *
 * <p>LLM/Mock 解析失败时兜底：纯规则将简历文本按标题切分为卡片结构并给
 * 确定性能力模型评分（不依赖任何外部服务），保证解析链路始终可闭环。
 * 同时作为 parse_resume 工具的执行实现（Mock 链路下工具结果与降级同源）。
 *
 * <p>遵循系分文档 3.5「只分段、不分类」原则：识别到的标准章节进 points[]，
 * 无法识别的长文本进 confidence=LOW + raw_text，不臆造内容。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleParseFallback {

    /** 标准章节关键字（按顺序匹配，标题行判定用；中英文简历均支持） */
    /**
     * 章节标题关键字（大小写不敏感匹配）
     * <p>覆盖常见中文简历标题变体：教育背景/实习经历/校园经历等（不限于"经历"后缀）。
     */
    private static final String[] SECTION_KEYWORDS = {
            // 个人信息
            "个人信息", "基本信息", "个人简介", "个人资料", "联系方式", "基本资料",
            // 教育
            "教育背景", "教育经历", "教育", "学历", "在校经历",
            // 工作/实习
            "工作经历", "工作", "实习经历", "实习", "社会实践", "校园经历", "任职经历", "职业经历",
            // 项目
            "项目经历", "项目经验", "项目",
            // 技能
            "专业技能", "技能特长", "技能", "专业能力", "能力",
            // 自我评价
            "自我评价", "个人评价", "自我评估", "自我介绍",
            // 荣誉证书
            "荣誉", "荣誉奖项", "获奖", "奖项", "证书", "资格认证", "资质",
            // 其他
            "培训", "语言能力", "语言", "兴趣爱好", "爱好", "社团", "特长", "求职意向",
            // 英文
            "basic info", "personal info", "education", "work experience",
            "internship", "project experience", "project", "skills", "self evaluation",
            "certificates", "languages", "interests", "summary", "objective"
    };

    /** 章节标题编号前缀（"一、" "1." "（1）" "1、" 等），判定标题前剥离 */
    private static final Pattern TITLE_PREFIX_PATTERN = Pattern.compile(
            "^(?:[一二三四五六七八九十百]+[、.．]|\\d+[、.．]|[（(]\\d+[）)])\\s*");

    /**
     * 嵌入图片文件名行（Tika 提取 docx 时会把 word/media/ 内图片的引用名混进文本流，
     * 如 image4.png / image5.svg / 图片 1.png），非简历内容，分段时过滤。
     */
    private static final Pattern IMAGE_FILE_PATTERN = Pattern.compile(
            "(?i)^(image|img|图片|pic)\\s*\\d+\\s*\\.(png|jpe?g|gif|svg|bmp|webp)$");

    /** 技能引导词 */
    private static final Pattern SKILL_PATTERN =
            Pattern.compile("(?:熟练掌握|精通|熟悉|了解)[：:\\s]*(.+?)($|[。；;])");

    /** 工作年限 */
    private static final Pattern YEARS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*年");

    /** 行业关键词 */
    private static final String[] INDUSTRY_KEYWORDS = {
            "java", "python", "javascript", "前端", "后端", "数据", "产品", "运营",
            "测试", "设计", "管理", "市场", "开发", "算法", "架构"
    };

    /** 综合素质关键词 */
    private static final String[] QUALITY_KEYWORDS = {
            "沟通", "团队", "协作", "抗压", "责任心", "主动", "学习能力", "执行力"
    };

    /** 学习成长关键词 */
    private static final String[] GROWTH_KEYWORDS = {
            "证书", "培训", "课程", "奖学金", "竞赛", "认证", "自学", "开源"
    };

    private final ObjectMapper objectMapper;

    /**
     * 规则解析入口（可重复调用，纯函数式）
     */
    public AgentResult parse(String text) {
        List<Section> sections = segment(text);
        ObjectNode cardStructure = buildCardStructure(sections);
        Map<String, Integer> scores = score(text);

        AgentResult result = new AgentResult();
        result.setCardStructure(cardStructure);
        result.setProfessionalSkillScore(scores.get("professional_skill"));
        result.setWorkExperienceScore(scores.get("work_experience"));
        result.setIndustryKnowledgeScore(scores.get("industry_knowledge"));
        result.setComprehensiveQualityScore(scores.get("comprehensive_quality"));
        result.setLearningGrowthScore(scores.get("learning_growth"));
        result.setSubDimensions(buildSubDimensions(scores, sections));
        result.setResumeMd(buildMarkdown(sections));
        return result;
    }

    /**
     * 纯装配路径：按 Agent 已识别的章节边界组装 card_structure + 评分 + md
     *
     * <p>标题识别由 Agent（Mock/真实 LLM）语义理解完成并产出 {@link SectionDef} 列表，
     * 本方法不再做任何标题判定，只按边界组装结构化产出。
     *
     * @param sectionDefs Agent 识别的章节边界（title + lines）
     * @param fullText    Tika 提取的简历全文（评分与子维度基于全文）
     */
    public AgentResult buildFromSections(List<SectionDef> sectionDefs, String fullText) {
        List<Section> sections = convertToSections(sectionDefs);
        ObjectNode cardStructure = buildCardStructure(sections);
        Map<String, Integer> scores = score(fullText);

        AgentResult result = new AgentResult();
        result.setCardStructure(cardStructure);
        result.setProfessionalSkillScore(scores.get("professional_skill"));
        result.setWorkExperienceScore(scores.get("work_experience"));
        result.setIndustryKnowledgeScore(scores.get("industry_knowledge"));
        result.setComprehensiveQualityScore(scores.get("comprehensive_quality"));
        result.setLearningGrowthScore(scores.get("learning_growth"));
        result.setSubDimensions(buildSubDimensions(scores, sections));
        result.setResumeMd(buildMarkdown(sections));
        return result;
    }

    /**
     * Agent 章节定义 → 内部 Section（过滤空行/空标题）
     */
    private List<Section> convertToSections(List<SectionDef> sectionDefs) {
        List<Section> sections = new ArrayList<>();
        if (sectionDefs == null) {
            return sections;
        }
        for (SectionDef def : sectionDefs) {
            String title = def.getTitle() == null || def.getTitle().trim().isEmpty()
                    ? "未命名章节" : def.getTitle();
            Section section = new Section(title);
            if (def.getLines() != null) {
                for (String line : def.getLines()) {
                    if (line != null && !line.trim().isEmpty()) {
                        section.lines.add(line.trim());
                    }
                }
            }
            sections.add(section);
        }
        return sections;
    }

    // ==================== 分段 ====================

    private static class Section {
        final String title;
        final List<String> lines = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }
    }

    /**
     * 按标题行切分文本为章节（标题行 = 短行且含章节关键字）
     *
     * <p>公开给 {@code MockBaibaoxiangClient} 用作"LLM 语义分段"的确定性模拟：
     * 真实 LLM 接入后不再调用此方法（LLM 自行理解章节边界）。
     */
    public List<Section> segment(String text) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            // docx 嵌入图片文件名行（Tika 提取混入），非简历内容，跳过
            if (IMAGE_FILE_PATTERN.matcher(line).matches()) {
                continue;
            }
            if (isTitleLine(line)) {
                // 相邻同名标题去重（Tika 对 docx 标题段落可能双输出，如"教育背景"出现两次）
                if (!sections.isEmpty() && sections.get(sections.size() - 1).title.equals(line)) {
                    continue;
                }
                current = new Section(line);
                sections.add(current);
            } else {
                if (current == null) {
                    current = new Section("基本信息");
                    sections.add(current);
                }
                current.lines.add(line);
            }
        }
        return sections;
    }

    /**
     * 分段并转为 Agent 章节定义（供 Mock 模拟 LLM 语义分段使用）
     *
     * <p>等价于 LLM 从文本中"读出"章节边界；Mock 用它构造 parse_resume 的 sections 参数。
     */
    public List<SectionDef> segmentToDefs(String text) {
        List<Section> sections = segment(text);
        List<SectionDef> defs = new ArrayList<>();
        for (Section section : sections) {
            SectionDef def = new SectionDef();
            def.setTitle(section.title);
            def.setLines(new ArrayList<>(section.lines));
            defs.add(def);
        }
        return defs;
    }

    /**
     * 标题行判定（鲁棒版，兼容 Tika 提取的各种文本格式）：
     * <ol>
     *   <li>编号开头（"一、" "1." "（1）"）→ 一律视为标题（长度不限）</li>
     *   <li>行首命中章节关键字（标题与内容粘连，如"教育背景湖南科技大学…"）→ 视为标题</li>
     *   <li>短行（≤15 字）且含章节关键字 → 视为标题</li>
     *   <li>英文标题风格兜底</li>
     * </ol>
     */
    private boolean isTitleLine(String line) {
        // ① 编号开头即标题（无论长度，处理"一、个人信息"等）
        if (TITLE_PREFIX_PATTERN.matcher(line).find()) {
            return true;
        }
        // ①.5 "关键字+冒号+内容"是内容行而非标题（如"荣誉证书：CET-4""能力掌握：Java…"），
        // 排除后再走②③；"教育背景："（冒号后无内容）仍是标题
        if (isKeywordWithContentAfterColon(line)) {
            return false;
        }
        // ② 行首命中章节关键字（处理标题与内容粘连的超长行）
        if (startsWithKeyword(line)) {
            return true;
        }
        if (line.length() > 15) {
            return false;
        }
        // ③ 短行含关键字（剥离编号前缀后匹配，兼容无编号标题）
        String stripped = TITLE_PREFIX_PATTERN.matcher(line).replaceFirst("");
        String lower = stripped.toLowerCase();
        for (String keyword : SECTION_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        // ④ 英文简历兜底：无句尾标点且大写单词占比 > 50% 的短行视为标题
        // （如 "Education", "Work Experience"，避免正文行误判）
        return isEnglishTitleLike(line);
    }

    /** 行首命中章节关键字（处理"教育背景：湖南科技大学…"这类粘连行） */
    private boolean startsWithKeyword(String line) {
        String trimmed = line.trim();
        for (String keyword : SECTION_KEYWORDS) {
            if (trimmed.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "关键字 + [汉字] + 冒号 + 实质内容" → 内容行（如"荣誉证书：CET-4""能力掌握：Java…"，
     * 关键字"荣誉"/"能力"与冒号之间还有"证书"/"掌握"），不应判为章节标题。
     * 标题通常是纯词（"教育背景"）、"关键字+冒号"后无内容（"教育背景："）或粘连行（"教育背景湖南…"）。
     */
    private boolean isKeywordWithContentAfterColon(String line) {
        String trimmed = line.trim();
        for (String keyword : SECTION_KEYWORDS) {
            if (!trimmed.startsWith(keyword)) {
                continue;
            }
            // 关键字后扫描：汉字/空白继续（标题词扩展），遇冒号停止，其他字符则该分支不中
            String rest = trimmed.substring(keyword.length());
            int i = 0;
            while (i < rest.length()) {
                char c = rest.charAt(i);
                if (c == '：' || c == ':') {
                    break;
                }
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c < 0x4E00 || c > 0x9FFF) {
                    i = rest.length(); // 非汉字非冒号：不是"关键字…冒号"形态
                    break;
                }
                i++;
            }
            if (i >= rest.length() || (rest.charAt(i) != '：' && rest.charAt(i) != ':')) {
                continue;
            }
            // 冒号后有非空白内容 → 内容行
            int j = i + 1;
            while (j < rest.length() && Character.isWhitespace(rest.charAt(j))) {
                j++;
            }
            return j < rest.length();
        }
        return false;
    }

    /**
     * 英文标题风格判定：至少 2 个词且每个词首字母大写，或单一大写开头词且行尾无标点
     */
    private boolean isEnglishTitleLike(String line) {
        if (!line.matches("[a-zA-Z ]+")) {
            return false;
        }
        if (line.endsWith(".") || line.endsWith(",") || line.endsWith(";") || line.endsWith(":")) {
            return false;
        }
        String[] words = line.trim().split("\\s+");
        if (words.length == 1) {
            // 单个词：首字母大写且非英文停用词
            String first = words[0];
            return !first.isEmpty() && Character.isUpperCase(first.charAt(0))
                    && !ENGLISH_STOP_WORDS.contains(first.toLowerCase());
        }
        // 多个词：全部首字母大写（或含常见标题冠词/连词）
        int upperCount = 0;
        for (String word : words) {
            if (!word.isEmpty() && Character.isUpperCase(word.charAt(0))) {
                upperCount++;
            }
        }
        return upperCount >= words.length - 1;
    }

    /** 英文停用词（单次出现时不视为标题） */
    private static final java.util.Set<String> ENGLISH_STOP_WORDS = new java.util.HashSet<>(
            java.util.Arrays.asList("name", "phone", "email", "summary", "introduction", "profile"));

    // ==================== 卡片结构 ====================

    private ObjectNode buildCardStructure(List<Section> sections) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode sectionNodes = root.putArray("sections");
        for (Section section : sections) {
            ObjectNode node = sectionNodes.addObject();
            node.put("title", section.title);
            ArrayNode points = node.putArray("points");
            for (String line : section.lines) {
                ObjectNode point = points.addObject();
                point.put("id", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                point.put("text", line);
            }
            if (points.size() == 0) {
                // 空章节（只有标题）：标记 LOW 交由前端展示原始标题
                node.put("confidence", "LOW");
                node.put("raw_text", section.title);
            } else {
                node.put("confidence", "HIGH");
            }
        }
        root.put("confidence", sections.isEmpty() ? "LOW" : "HIGH");
        return root;
    }

    // ==================== 能力模型评分（确定性规则） ====================

    private Map<String, Integer> score(String text) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("professional_skill", scoreProfessionalSkill(text));
        scores.put("work_experience", scoreWorkExperience(text));
        scores.put("industry_knowledge", scoreIndustryKnowledge(text));
        scores.put("comprehensive_quality", scoreByKeywords(text, QUALITY_KEYWORDS, 55, 5));
        scores.put("learning_growth", scoreByKeywords(text, GROWTH_KEYWORDS, 50, 6));
        return scores;
    }

    private int scoreProfessionalSkill(String text) {
        int skillCount = 0;
        int proficient = 0;
        Matcher matcher = SKILL_PATTERN.matcher(text);
        while (matcher.find()) {
            // 技能列表以逗号/顿号分隔，统计技能词数量
            skillCount += matcher.group(1).split("[，,、\\s]+").length;
            if (matcher.group(1).length() > 0) {
                proficient++;
            }
        }
        return Math.min(90, 55 + skillCount * 5 + proficient * 3);
    }

    private int scoreWorkExperience(String text) {
        Matcher matcher = YEARS_PATTERN.matcher(text);
        double years = 0;
        while (matcher.find()) {
            years = Math.max(years, Double.parseDouble(matcher.group(1)));
        }
        if (years <= 0) {
            return 40;
        }
        return Math.min(90, 50 + (int) (years * 8));
    }

    private int scoreIndustryKnowledge(String text) {
        String lower = text.toLowerCase();
        int hits = 0;
        for (String keyword : INDUSTRY_KEYWORDS) {
            if (lower.contains(keyword)) {
                hits++;
            }
        }
        return 55 + Math.min(hits, 6) * 5;
    }

    private int scoreByKeywords(String text, String[] keywords, int base, int step) {
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return base + Math.min(hits, 7) * step;
    }

    /**
     * 子维度明细：技能词、工作年限、章节命中情况（JSON 字符串）
     */
    private String buildSubDimensions(Map<String, Integer> scores, List<Section> sections) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            ArrayNode skills = node.putArray("skills");
            // 从"专业技能/Skills"章节提取技能词（中英文均支持）
            for (Section section : sections) {
                if (section.title.contains("技能") || section.title.contains("专业")
                        || section.title.toLowerCase().contains("skill")) {
                    for (String line : section.lines) {
                        for (String word : line.split("[，,、\\s]+")) {
                            if (word.length() >= 2 && word.length() <= 20 && !word.matches(".*\\d{6,}.*")) {
                                skills.add(word);
                            }
                        }
                    }
                }
            }
            Matcher matcher = YEARS_PATTERN.matcher(sections.isEmpty() ? "" : sections.get(0).lines.toString());
            if (matcher.find()) {
                node.put("work_years", matcher.group(1));
            }
            ArrayNode sectionTitles = node.putArray("sections");
            for (Section section : sections) {
                sectionTitles.add(section.title);
            }
            return node.toString();
        } catch (Exception e) {
            log.warn("子维度明细构建失败，返回空", e);
            return "{}";
        }
    }

    // ==================== Markdown 简历 ====================

    private String buildMarkdown(List<Section> sections) {
        StringBuilder md = new StringBuilder("# 个人简历\n\n");
        for (Section section : sections) {
            md.append("## ").append(section.title).append("\n\n");
            if (section.lines.isEmpty()) {
                md.append("> :warning: ").append(section.title).append("（无详细内容）\n\n");
            } else {
                for (String line : section.lines) {
                    md.append("- ").append(line).append("\n");
                }
            }
            md.append("\n");
        }
        return md.toString();
    }

    /**
     * card_structure 序列化 JSON 文本（供 Agent final 输出组装）
     */
    public String cardStructureToJson(JsonNode cardStructure) {
        return cardStructure == null ? "{}" : cardStructure.toString();
    }
}

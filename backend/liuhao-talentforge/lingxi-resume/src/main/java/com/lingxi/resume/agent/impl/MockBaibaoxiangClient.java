package com.lingxi.resume.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.MinioUtil;
import com.lingxi.resume.agent.A2AEvent;
import com.lingxi.resume.config.ResumeStorageClient;
import com.lingxi.resume.agent.AgentResult;
import com.lingxi.resume.agent.BaibaoxiangClient;
import com.lingxi.resume.agent.RuleParseFallback;
import com.lingxi.resume.agent.SectionDef;
import com.lingxi.resume.agent.TikaTextExtractor;
import com.lingxi.resume.exception.ResumeErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mock A2A 智能体客户端（演示/验证链路用）
 *
 * <p>内部从 MinIO 下载 PDF → Tika 提取文本 → 规则引擎分段装配，
 * 产出 TextEvent + ArtifactEvent + StatusEvent。PII 只存在于 JVM 内存，不离开本地。
 *
 * <p>个人信息（name/phone/email/wechat）用规则引擎的章节 + 正则从本地文本提取，
 * 模拟真实 LLM 的结构化提取（准确度低于 LLM，但用于链路演示足够）。
 *
 * <p>真实百宝箱接入后，本实现被 {@code RealBaibaoxiangClient}（mock=false）替换。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "resume.agent.mock", havingValue = "true")
public class MockBaibaoxiangClient implements BaibaoxiangClient {

    /** 事件间隔（模拟流式节奏，章节级进度逐条推送） */
    private static final long EVENT_INTERVAL_MS = 300;

    private final RuleParseFallback ruleParseFallback;
    private final TikaTextExtractor tikaTextExtractor;
    private final MinioUtil minioUtil;
    private final ResumeStorageClient storageClient;
    private final ObjectMapper objectMapper;

    @Override
    public Iterable<A2AEvent> chatA2A(String pdfFileUrl, String contextId, AtomicBoolean cancelled) {
        return chatA2A(pdfFileUrl, null, contextId, cancelled);
    }

    @Override
    public Iterable<A2AEvent> chatText(String text, String mode, String contextId, AtomicBoolean cancelled) {
        List<A2AEvent> events = new ArrayList<>();
        if (MODE_DIAGNOSIS.equals(mode)) {
            events.add(new A2AEvent.TextEvent("Mock诊断：正在分析简历与目标职业的匹配度..."));
            String mockReport = "## 诊断报告\n\n### 综合匹配度: 72.5\n\n"
                    + "🔴 **硬伤项**：缺少云计算相关项目经验\n"
                    + "🟠 **薄弱项**：英语水平未达到外企要求\n"
                    + "✅ **优势项**：5年后端开发经验，Spring Cloud微服务架构能力突出\n"
                    + "⚪ **冗余项**：2015年的实习经历可删减";
            events.add(new A2AEvent.ArtifactEvent("diag-1",
                    "{\"matchScore\":72.5,\"report_md\":\"" + mockReport.replace("\"", "\\\"") + "\"}", true));
            events.add(new A2AEvent.StatusEvent("completed", true));
        } else {
            events.add(new A2AEvent.TextEvent("Mock: 不支持的文本模式 " + mode));
            events.add(new A2AEvent.StatusEvent("failed", true));
        }
        return events;
    }

    @Override
    public Iterable<A2AEvent> chatA2A(String pdfFileUrl, String mode, String contextId, AtomicBoolean cancelled) {
        // ① 内部从存储下载 PDF → Tika 提取纯文本（PII 不离 JVM）
        String tikaText;
        String objectName = storageClient.parseObjectName(pdfFileUrl);
        if (objectName == null) {
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                    "无法解析存储 objectName: " + pdfFileUrl);
        }
        try (InputStream is = storageClient.getObject(objectName)) {
            tikaText = tikaTextExtractor.extractFromStream(is);
        } catch (Exception e) {
            log.error("Mock 内部 Tika 提取失败: url={}", pdfFileUrl, e);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历文本提取失败", e);
        }

        List<A2AEvent> events = new ArrayList<>();

        // ② 模拟 LLM 语义理解 → 识别章节边界
        List<SectionDef> sectionDefs = ruleParseFallback.segmentToDefs(tikaText);
        events.add(new A2AEvent.TextEvent("正在分析简历文本结构……"));
        events.add(new A2AEvent.TextEvent("识别到 " + sectionDefs.size() + " 个章节："
                + sectionDefs.stream().map(SectionDef::getTitle)
                .collect(Collectors.joining("、"))));

        // ③ 逐章节推送 progress（模拟 LLM 流式生成 card_structure，前端逐张渲染卡片骨架）
        List<String> doneTitles = new ArrayList<>();
        for (SectionDef def : sectionDefs) {
            doneTitles.add(def.getTitle());
            events.add(new A2AEvent.ProgressEvent("card", doneTitles.size(),
                    new ArrayList<>(doneTitles)));
        }

        // ④ 模拟 LLM 结构化装配（本地规则引擎）
        AgentResult result = ruleParseFallback.buildFromSections(sectionDefs, tikaText);
        // ⑤ 模拟 LLM 提取个人信息（正则，本地）
        extractPersonalFromText(tikaText, result);
        String outputJson = buildOutputJson(result);

        // ⑥ artifact-update + status-update
        events.add(new A2AEvent.ArtifactEvent("artifact-1", outputJson, true));
        events.add(new A2AEvent.StatusEvent("completed", true));

        return () -> new Iterator<A2AEvent>() {
            private final Iterator<A2AEvent> inner = events.iterator();

            @Override
            public boolean hasNext() {
                return inner.hasNext() && !cancelled.get();
            }

            @Override
            public A2AEvent next() {
                try {
                    Thread.sleep(EVENT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return inner.next();
            }
        };
    }

    /**
     * 从本地文本中正则提取个人信息（模拟 LLM 的语义提取，仅本地 JVM）
     */
    private void extractPersonalFromText(String text, AgentResult result) {
        // 姓名：取第一行第一个连续汉字片段
        Matcher nameMatcher = Pattern.compile("[\\u4e00-\\u9fff]{2,6}").matcher(text);
        if (nameMatcher.find()) {
            result.setName(nameMatcher.group());
        }
        // 手机号：连续 11 位 1[3-9]
        Matcher phoneMatcher = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)").matcher(text);
        if (phoneMatcher.find()) {
            result.setPhone(phoneMatcher.group());
        }
        // 邮箱
        Matcher emailMatcher = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").matcher(text);
        if (emailMatcher.find()) {
            result.setEmail(emailMatcher.group());
        }
        // 微信号（标签匹配）
        Matcher wechatMatcher = Pattern.compile("(?i)(wechat|微信)\\s*[:：]\\s*(\\S+)").matcher(text);
        if (wechatMatcher.find()) {
            result.setWechat(wechatMatcher.group(2));
        }
    }

    /**
     * AgentResult → artifact JSON（含 personal，与 LLM 返回格式一致）
     */
    private String buildOutputJson(AgentResult result) {
        Map<String, Object> output = new LinkedHashMap<>();

        // personal（LLM 结构化提取的个人信息）
        Map<String, Object> personal = new LinkedHashMap<>();
        personal.put("name", result.getName());
        personal.put("phone", result.getPhone());
        personal.put("email", result.getEmail());
        personal.put("wechat", result.getWechat());
        output.put("personal", personal);

        output.put("card_structure", result.getCardStructure());

        Map<String, Object> abilityModel = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("professional_skill", result.getProfessionalSkillScore());
        scores.put("work_experience", result.getWorkExperienceScore());
        scores.put("industry_knowledge", result.getIndustryKnowledgeScore());
        scores.put("comprehensive_quality", result.getComprehensiveQualityScore());
        scores.put("learning_growth", result.getLearningGrowthScore());
        abilityModel.put("scores", scores);
        abilityModel.put("sub_dimensions", result.getSubDimensions());
        output.put("ability_model", abilityModel);

        output.put("resume_md", result.getResumeMd());

        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.warn("artifact JSON 序列化失败", e);
            return "{}";
        }
    }
}

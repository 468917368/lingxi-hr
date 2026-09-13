package com.lingxi.resume.agent;

import com.lingxi.common.exception.BusinessException;
import com.lingxi.resume.config.ResumeStorageClient;
import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.exception.ResumeErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Tika 简历文本提取 + 质量校验
 *
 * <p>从 MinIO 读取简历原件，Tika 自动识别格式并提取纯文本，
 * 按系分文档 3.5 做质量预检：
 * <ul>
 *   <li>文本长度 &lt; 100 字 → 不合格（如扫描件/空 PDF/纯图片简历）</li>
 *   <li>乱码占比 &gt; 30%（非可打印字符）→ 不合格</li>
 * </ul>
 * 不合格抛 {@link BusinessException}（PARSE_FAILED），由编排层置 FAILED 并逻辑删除。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TikaTextExtractor {

    /** 文本质量下限（字符数） */
    private static final int MIN_TEXT_LENGTH = 100;

    /** 乱码容忍上限（非可打印字符占比） */
    private static final double MAX_GARBLE_RATIO = 0.3;

    /** 提取文本长度上限（10MB 文件量级） */
    private static final int MAX_TEXT_LENGTH = 10 * 1024 * 1024;

    /**
     * 嵌入图片文件名行（Tika 提取 docx 时把 word/media/ 内图片的引用名混进文本流，
     * 如 image4.png / image5.svg / 图片 1.png）。在源头（Tika 出口）过滤，
     * 否则百宝箱会按"完整保留原文"把它们收进卡片、规则引擎降级也会收进 section。
     */
    private static final Pattern IMAGE_FILE_PATTERN = Pattern.compile(
            "(?i)^(image|img|图片|pic)\\s*\\d+\\s*\\.(png|jpe?g|gif|svg|bmp|webp)$");

    private final ResumeStorageClient storageClient;

    /**
     * 提取并校验简历文本
     *
     * @param resume 简历记录（fileUrl 指向存储原件）
     * @return 通过质量校验的纯文本
     * @throws BusinessException 提取失败或质量不合格
     */
    public String extract(Resume resume) {
        String objectName = storageClient.parseObjectName(resume.getFileUrl());
        if (objectName == null) {
            log.warn("简历文件地址无法解析 objectName: resumeId={}, fileUrl={}",
                    resume.getId(), resume.getFileUrl());
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历文件不存在，无法解析");
        }
        try (InputStream inputStream = storageClient.getObject(objectName)) {
            return extractFromStream(inputStream);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历文本提取失败: resumeId={}, object={}", resume.getId(), objectName, e);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历文本提取失败", e);
        }
    }

    /**
     * 从输入流提取并校验简历文本（Mock 内部用，PII 不离 JVM）
     *
     * @param inputStream PDF/Word 文件流
     * @return 通过质量校验的纯文本
     * @throws BusinessException 提取失败或质量不合格
     */
    public String extractFromStream(InputStream inputStream) {
        String text;
        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(inputStream, handler, new Metadata(), new ParseContext());
            text = handler.toString().trim();
        } catch (Exception e) {
            log.error("简历文本提取失败（流）", e);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历文本提取失败", e);
        }

        // docx 嵌入图片文件名行（Tika 混入的 word/media/ 引用名）→ 源头过滤，
        // 保证百宝箱/规则引擎/MD 兜底三条链路拿到的都是干净文本
        text = removeImageFileLines(text);

        if (text.length() < MIN_TEXT_LENGTH) {
            log.warn("简历文本过短，判定不合格: len={}", text.length());
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                    "简历内容无法读取（文本过少，可能为扫描件或图片）");
        }
        if (garbleRatio(text) > MAX_GARBLE_RATIO) {
            log.warn("简历文本乱码占比过高，判定不合格");
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                    "简历内容无法读取（编码异常）");
        }
        // 替换字符（U+FFFD）源头清理（2026-08-12 决策）：不传 � 给百宝箱——
        // � 是 PDF 生成端编码损坏的标记（数字字体子集无 ToUnicode，提取无解）。
        // 替换为空格而非删除：删除会让 "1730��303" 粘连成 "1730303" 改变数字；
        // 空格保留可辨识性，且连续空格成为"损坏标记"，由 buildQuery 指令（REPLACEMENT_CHAR_HINT）
        // 告知 LLM 省略含异常空格的字段/要点，用户在线修改补齐。
        if (countReplacementChars(text) > 0) {
            log.warn("简历文本含替换字符（U+FFFD），替换为空格后发送（不传 � 给百宝箱）: count={}",
                    countReplacementChars(text));
        }
        text = text.replace('�', ' ');
        log.info("简历文本提取成功: textLen={}", text.length());
        return text;
    }

    /**
     * 过滤嵌入图片文件名行（整行匹配才过滤，不影响正常内容行）
     */
    private String removeImageFileLines(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (String rawLine : text.split("\r?\n")) {
            if (IMAGE_FILE_PATTERN.matcher(rawLine.trim()).matches()) {
                continue;
            }
            sb.append(rawLine).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 乱码占比：非可打印字符（保留常见空白、ASCII 可打印、CJK 汉字、全角标点）占比
     */
    private double garbleRatio(String text) {
        int total = 0;
        int garbled = 0;
        // 抽样前 5000 字符估算占比，避免超长文本遍历开销
        int limit = Math.min(text.length(), 5000);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            total++;
            if (!isPrintable(c)) {
                garbled++;
            }
        }
        return total == 0 ? 0 : (double) garbled / total;
    }

    private boolean isPrintable(char c) {
        if (c == '\n' || c == '\r' || c == '\t') {
            return true;
        }
        if (c >= 0x20 && c <= 0x7E) {
            return true;                    // ASCII 可打印
        }
        if (c >= 0x4E00 && c <= 0x9FFF) {
            return true;                    // CJK 汉字
        }
        if (Character.isLetterOrDigit(c)) {
            return true;
        }
        // 常见全角标点/中文符号范围。
        // 0xFFFD（替换字符 �）不在 0xFF00-0xFFEF 内，本就判乱码——garbleRatio 30% 阈值
        // 负责整体乱码拦截；零星 �（占比低）由 extractFromStream 识别告警、原样保留
        // 交 LLM 有限推断（2026-08-12，配合 buildQuery 的 REPLACEMENT_CHAR_HINT）
        return (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }

    /** 统计替换字符（U+FFFD）出现次数（识别用，不拦截） */
    private int countReplacementChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '�') {
                count++;
            }
        }
        return count;
    }

}

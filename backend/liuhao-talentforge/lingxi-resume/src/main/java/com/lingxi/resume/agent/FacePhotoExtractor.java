package com.lingxi.resume.agent;

import com.lingxi.common.util.MinioUtil;
import com.lingxi.resume.config.ResumeStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 求职者头像提取器（PDF / DOCX → 候选图片 → MinIO）
 *
 * <p>从简历原件中提取嵌入图片，按启发式规则筛选最可能的头像，
 * 上传 MinIO 并返回 objectName（签名在接口返回时动态生成，避免固定 presigned URL 过期）。
 *
 * <p>格式分发：
 * <ul>
 *   <li>{@code .pdf} → PDFBox 解析第 1 页图片资源</li>
 *   <li>{@code .docx} → JDK ZipInputStream 读取 {@code word/media/} 嵌入图片（docx 为 ZIP 结构，零新依赖）</li>
 *   <li>{@code .doc} → 走 docx 分支（OLE2 非 ZIP，ZIP 迭代自然无条目，返回 null）</li>
 *   <li>其他 → 返回 null</li>
 * </ul>
 *
 * <p>启发式筛选（不依赖人脸识别库，准确度够用）：
 * <ul>
 *   <li>仅扫描首页图片（PDF 第 1 页 / docx 全部 media——docx 头像通常也是首页顶部插入，media 中图片数量有限）</li>
 *   <li>宽高均 ≥ {@link #MIN_SIDE_PX}（过滤图标/logo/装饰元素）</li>
 *   <li>0.6 ≤ 宽高比 ≤ 1.0（头像通常近方形或竖长方形，过滤横条 banner/二维码）</li>
 *   <li>多候选取面积最大者（头像通常比装饰元素大）</li>
 * </ul>
 *
 * <p>容错：任何异常（文件损坏/无嵌入图/提取失败）均返回 null，不影响解析主流程。
 *
 * @author 成员C
 * @since 2026-08-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacePhotoExtractor {

    /** 候选图片最小边长（像素）：过滤图标/logo/二维码 */
    private static final int MIN_SIDE_PX = 100;

    /** 宽高比下限 */
    private static final double MIN_ASPECT_RATIO = 0.6;

    /** 宽高比上限 */
    private static final double MAX_ASPECT_RATIO = 1.0;

    /** 头像 objectName 模板 */
    private static final String FACE_OBJECT_TEMPLATE = "resumes/%d/face.png";

    /** docx 嵌入图片目录前缀（docx 为 ZIP 结构，图片位于 word/media/） */
    private static final String DOCX_MEDIA_PREFIX = "word/media/";

    /** docx 可接受图片格式（ImageIO 可解码的常见格式；WebP/TIFF 罕见不入列） */
    private static final Set<String> IMAGE_SUFFIXES = new HashSet<>(
            Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".bmp"));

    private final MinioUtil minioUtil;
    private final ResumeStorageClient storageClient;

    /**
     * 提取头像并上传 MinIO（按文件扩展名分发：pdf / docx / doc）
     *
     * @param fileObjectName 简历原件的 MinIO objectName
     * @param resumeId       简历ID（用于头像 objectName）
     * @return 头像 objectName（如 resumes/{id}/face.png）；无候选/失败返回 null（不抛异常）
     */
    public String extractAndUpload(String fileObjectName, Long resumeId) {
        if (fileObjectName == null) {
            log.debug("头像提取: objectName 为空，跳过");
            return null;
        }
        String lower = fileObjectName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return extractFromPdf(fileObjectName, resumeId);
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return extractFromDocx(fileObjectName, resumeId);
        }
        log.debug("非 PDF/DOCX 文件，跳过头像提取: object={}", fileObjectName);
        return null;
    }

    // ==================== PDF 分支（PDFBox） ====================

    /**
     * PDF 头像提取：扫描第 1 页图片资源，启发式筛选最佳候选，转 PNG 上传
     */
    private String extractFromPdf(String pdfObjectName, Long resumeId) {
        PDDocument document = null;
        try (InputStream is = storageClient.getObject(pdfObjectName)) {
            document = PDDocument.load(is);

            // 遍历第 1 页图片资源，筛选最佳候选
            PDImageXObject best = null;
            long bestArea = 0;
            if (document.getNumberOfPages() > 0) {
                PDPage page = document.getPage(0);
                PDResources resources = page.getResources();
                // 空白 PDF 页可能无 resources（getXObjectNames 会 NPE），空则跳过
                if (resources != null) {
                    for (COSName name : resources.getXObjectNames()) {
                        PDXObject xObject = resources.getXObject(name);
                        if (!(xObject instanceof PDImageXObject)) {
                            continue;
                        }
                        PDImageXObject image = (PDImageXObject) xObject;
                        if (!isGoodCandidate(image.getWidth(), image.getHeight())) {
                            log.debug("头像候选被过滤: name={}, w={}, h={}", name,
                                    image.getWidth(), image.getHeight());
                            continue;
                        }
                        long area = (long) image.getWidth() * image.getHeight();
                        if (area > bestArea) {
                            bestArea = area;
                            best = image;
                        }
                    }
                }
            }

            if (best == null) {
                log.info("头像提取: 无合适候选图片, resumeId={}", resumeId);
                return null;
            }

            // 转 BufferedImage → PNG 字节
            BufferedImage image = best.getImage();
            return uploadAsPng(image, resumeId, best.getWidth(), best.getHeight());
        } catch (Exception e) {
            log.warn("头像提取失败（跳过，不影响解析主流程）: resumeId={}", resumeId, e);
            return null;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    log.debug("PDF 文档关闭失败: resumeId={}", resumeId, e);
                }
            }
        }
    }

    // ==================== DOCX 分支（ZipInputStream） ====================

    /**
     * DOCX 头像提取：docx 为 ZIP 结构，图片嵌在 word/media/ 下。
     * 遍历条目 → ImageIO 解码 → 同一启发式筛选 → 面积最大者转 PNG 上传。
     *
     * <p>.doc（OLE2 非 ZIP）走到这里：ZipInputStream 抛异常或零条目，被外层 catch 吞掉返回 null。
     */
    private String extractFromDocx(String docxObjectName, Long resumeId) {
        try (InputStream is = storageClient.getObject(docxObjectName);
             ZipInputStream zip = new ZipInputStream(is)) {
            BufferedImage best = null;
            long bestArea = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || !name.startsWith(DOCX_MEDIA_PREFIX)) {
                    zip.closeEntry();
                    continue;
                }
                String lower = name.toLowerCase();
                boolean imageSuffix = IMAGE_SUFFIXES.stream().anyMatch(lower::endsWith);
                if (!imageSuffix) {
                    zip.closeEntry();
                    continue;
                }
                // 读条目字节 → 解码（失败跳过该条目，继续迭代）
                try {
                    BufferedImage image = ImageIO.read(zip);
                    if (image == null) {
                        log.debug("docx 图片解码失败（跳过）: entry={}", name);
                        continue;
                    }
                    if (!isGoodCandidate(image.getWidth(), image.getHeight())) {
                        log.debug("docx 头像候选被过滤: entry={}, w={}, h={}", name,
                                image.getWidth(), image.getHeight());
                        continue;
                    }
                    long area = (long) image.getWidth() * image.getHeight();
                    if (area > bestArea) {
                        bestArea = area;
                        best = image;
                    }
                } catch (Exception decodeFailed) {
                    log.debug("docx 图片条目处理失败（跳过）: entry={}, error={}",
                            name, decodeFailed.getMessage());
                }
            }
            if (best == null) {
                log.info("头像提取: 无合适候选图片, resumeId={}", resumeId);
                return null;
            }
            return uploadAsPng(best, resumeId, best.getWidth(), best.getHeight());
        } catch (Exception e) {
            log.warn("头像提取失败（跳过，不影响解析主流程）: resumeId={}", resumeId, e);
            return null;
        }
    }

    // ==================== 公共逻辑 ====================

    /**
     * BufferedImage → PNG 字节 → 上传 MinIO（桶私有，返回 objectName 落库——签名由接口返回时动态生成）
     */
    private String uploadAsPng(BufferedImage image, Long resumeId, int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                log.warn("头像 PNG 编码失败（跳过）: resumeId={}", resumeId);
                return null;
            }
            String objectName = String.format(FACE_OBJECT_TEMPLATE, resumeId);
            try (ByteArrayInputStream stream = new ByteArrayInputStream(out.toByteArray())) {
                minioUtil.upload(objectName, stream, "image/png");
            }
            log.info("头像提取成功: resumeId={}, size={}x{}, object={}",
                    resumeId, width, height, objectName);
            return objectName;
        } catch (Exception e) {
            log.warn("头像上传失败（跳过）: resumeId={}", resumeId, e);
            return null;
        }
    }

    /**
     * 启发式筛选：尺寸 + 宽高比（PDF / DOCX 共用同一规则）
     */
    private boolean isGoodCandidate(int width, int height) {
        if (width < MIN_SIDE_PX || height < MIN_SIDE_PX) {
            return false;
        }
        double ratio = (double) width / height;
        return ratio >= MIN_ASPECT_RATIO && ratio <= MAX_ASPECT_RATIO;
    }

}

package com.lingxi.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageRequest;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.MinioUtil;
import com.lingxi.common.util.SnowflakeIdUtil;
import com.lingxi.resume.agent.ResumeParseService;
import com.lingxi.resume.config.ResumeStorageClient;
import com.lingxi.resume.domain.dto.ResumeQuery;
import com.lingxi.resume.domain.dto.UpdateResumeDTO;
import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.domain.vo.InternalResumeVO;
import com.lingxi.resume.domain.vo.ResumeDetailVO;
import com.lingxi.resume.domain.vo.ResumeVO;
import com.lingxi.resume.domain.vo.UploadResumeVO;
import com.lingxi.resume.exception.ResumeErrorCode;
import com.lingxi.resume.mapper.ResumeMapper;
import com.lingxi.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 简历服务实现
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    /** 简历数量上限 */
    private static final int MAX_RESUME_COUNT = 5;

    /**
     * 文件格式白名单（对齐前端上传限制，2026-08-08 收窄）
     *
     * <p>移除 jpg/png：Tika 无 OCR 配置，图片简历提取不出文本（<100 字质量预检必不过），
     * 上传后必然解析失败并被逻辑删除；前端 accept/提示已同步移除。
     */
    private static final Set<String> ALLOWED_FORMATS = new HashSet<>(
            Arrays.asList("pdf", "docx", "doc"));

    /** 最大文件大小：10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    /** 上传进度 Redis 键前缀（Hash，10min） */
    private static final String UPLOAD_PROGRESS_KEY = "resume:upload:";

    /** 上传进度 TTL（分钟） */
    private static final long UPLOAD_PROGRESS_TTL_MINUTES = 10;

    private final ResumeMapper resumeMapper;
    private final MinioUtil minioUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ResumeParseService resumeParseService;
    /** 统一存储客户端（生成头像等 presigned URL，按 storage.type 分发 MinIO/OSS） */
    private final ResumeStorageClient storageClient;

    @Override
    public PageResult<ResumeVO> listResumes(PageRequest pageRequest) {
        Long candidateId = getLoginUserId();

        ResumeQuery query = new ResumeQuery();
        query.setCandidateId(candidateId);

        PageHelper.startPage(pageRequest.getPage(), pageRequest.getPageSize());
        List<Resume> list = resumeMapper.selectByCondition(query);

        List<ResumeVO> voList = list.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());

        PageInfo<Resume> pageInfo = new PageInfo<>(list);
        log.info("简历列表查询: candidateId={}, total={}", candidateId, pageInfo.getTotal());
        return PageResult.of(voList, pageInfo.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    @Transactional
    public UploadResumeVO upload(MultipartFile file) {
        Long candidateId = getLoginUserId();

        // ① 数量上限校验（仅统计未删除）
        int count = resumeMapper.countByCandidateId(candidateId);
        if (count >= MAX_RESUME_COUNT) {
            log.warn("上传简历: 已达{}份上限, candidateId={}", MAX_RESUME_COUNT, candidateId);
            throw new BusinessException(ResumeErrorCode.FILE_COUNT_LIMIT);
        }

        // ② 格式校验（白名单）
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileFormat = extractFormat(fileName);
        if (!ALLOWED_FORMATS.contains(fileFormat)) {
            log.warn("上传简历: 格式不支持, candidateId={}, fileName={}", candidateId, fileName);
            throw new BusinessException(ResumeErrorCode.FORMAT_NOT_SUPPORTED);
        }

        // ③ 大小校验（空文件拒绝 + ≤10MB上限）
        long fileSize = file.getSize();
        if (fileSize == 0) {
            log.warn("上传简历: 文件为空, candidateId={}, fileName={}", candidateId, fileName);
            throw new BusinessException(ResumeErrorCode.FILE_EMPTY);
        }
        if (fileSize > MAX_FILE_SIZE) {
            log.warn("上传简历: 文件过大, candidateId={}, size={}", candidateId, fileSize);
            throw new BusinessException(ResumeErrorCode.FILE_TOO_LARGE);
        }

        // ③.5 内容格式检测（Tika 读文件头判断真实格式，防 HTML 改名为 PDF）
        String realMimeType;
        try (InputStream detectStream = file.getInputStream()) {
            realMimeType = new Tika().detect(detectStream);
        } catch (Exception e) {
            log.warn("上传简历: Tika 内容检测失败，按扩展名放行, candidateId={}, fileName={}",
                    candidateId, fileName, e);
            realMimeType = null;
        }
        if (realMimeType != null && !contentTypeOf(fileFormat).equals(realMimeType)) {
            log.warn("上传简历: 文件内容与扩展名不匹配, candidateId={}, fileName={}, "
                    + "声明格式={}, 真实MIME={}", candidateId, fileName, fileFormat, realMimeType);
            throw new BusinessException(ResumeErrorCode.FORMAT_NOT_SUPPORTED.getErrorCode(),
                    "文件内容与扩展名不匹配，请上传正确的 " + fileFormat.toUpperCase() + " 文件");
        }

        // ③.6 空白文档预检（PDF/Word：无文本且无图 → 直接拒收，避免入库后解析失败留孤儿文件）
        if ("pdf".equals(fileFormat)) {
            rejectBlankPdf(file, candidateId, fileName);
        } else if ("docx".equals(fileFormat) || "doc".equals(fileFormat)) {
            rejectBlankWord(file, fileFormat, candidateId, fileName);
        }

        // 上传进度缓存（预留，前端可轮询查询）
        String uploadId = SnowflakeIdUtil.nextIdStr();
        String progressKey = UPLOAD_PROGRESS_KEY + uploadId;
        updateUploadProgress(progressKey, "UPLOADING", 10);

        // ④ 上传原件到 MinIO：resumes/{resumeId}/{随机名}.{ext}
        Long resumeId = SnowflakeIdUtil.nextId();
        String objectName = "resumes/" + resumeId + "/" + SnowflakeIdUtil.nextIdStr() + "." + fileFormat;
        String fileUrl;
        try (InputStream inputStream = file.getInputStream()) {
            fileUrl = minioUtil.upload(objectName, inputStream, contentTypeOf(fileFormat));
        } catch (Exception e) {
            log.error("上传简历: MinIO上传失败, object={}", objectName, e);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "文件上传失败", e);
        }
        updateUploadProgress(progressKey, "SAVED", 100);

        // ⑤ 入库（PENDING），首份简历自动设为默认（保证投递时必有默认简历）
        Resume resume = new Resume();
        resume.setId(resumeId);
        resume.setCandidateId(candidateId);
        resume.setFileName(fileName);
        resume.setFileUrl(fileUrl);
        resume.setFileFormat(fileFormat);
        resume.setFileSize((int) fileSize);
        resume.setIsDefault(count == 0 ? 1 : 0);
        resume.setParseStatus("PENDING");
        try {
            resumeMapper.insert(resume);
        } catch (Exception e) {
            // 入库失败补偿：删除已上传的 MinIO 原件，避免孤儿文件
            minioUtil.delete(objectName);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历入库失败", e);
        }

        log.info("上传简历成功: resumeId={}, candidateId={}, fileName={}, format={}, size={}",
                resumeId, candidateId, fileName, fileFormat, fileSize);

        // 注：上传后不在此触发解析——解析由 SSE 订阅触发（ResumeParseService.subscribeAndWait
        // 对 PENDING 简历自动 triggerParse）。若上传后立即解析，会与前端 SSE 订阅竞态：
        // 前端订阅建立时简历已 COMPLETED，只能收到历史 final，thinking/progress 流式进度全部错过。
        // 前端兜底：upload 页列表加载时自动订阅 PENDING 简历触发解析。

        UploadResumeVO vo = new UploadResumeVO();
        vo.setResumeId(resumeId);
        vo.setFileUrl(fileUrl);
        vo.setParseStatus(resume.getParseStatus());
        return vo;
    }

    /**
     * 空白 PDF 预检：页数为0，或所有页既无文本也无图片 → 判定空白，抛 FILE_EMPTY。
     * 扫描件 PDF（无文本但有图）放行，交给解析链路 OCR。预检异常按非空白放行，不阻断上传。
     */
    private void rejectBlankPdf(MultipartFile file, Long candidateId, String fileName) {
        try (InputStream in = file.getInputStream()) {
            try (PDDocument document = PDDocument.load(in)) {
                if (document.getNumberOfPages() == 0) {
                    log.warn("上传简历: PDF 无页面, candidateId={}, fileName={}", candidateId, fileName);
                    throw new BusinessException(ResumeErrorCode.FILE_EMPTY);
                }
                String text = new PDFTextStripper().getText(document).trim();
                if (text.isEmpty() && !hasPdfImages(document)) {
                    log.warn("上传简历: PDF 为空白文档, candidateId={}, fileName={}", candidateId, fileName);
                    throw new BusinessException(ResumeErrorCode.FILE_EMPTY);
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            // 预检失败（加密/损坏/超时等）不阻断上传，交由解析链路现有质量校验兜底
            log.warn("上传简历: PDF 空白预检异常，按非空白放行, candidateId={}, fileName={}", candidateId, fileName, e);
        }
    }

    /**
     * 空白 Word 预检：无文本且无嵌入图片 → 判定空白，抛 FILE_EMPTY。
     * 含图片的 Word（扫描件贴图）放行，交给解析链路。预检异常按非空白放行，不阻断上传。
     */
    private void rejectBlankWord(MultipartFile file, String fileFormat, Long candidateId, String fileName) {
        try (InputStream in = file.getInputStream()) {
            String text;
            boolean hasImage;
            if ("docx".equals(fileFormat)) {
                try (XWPFDocument doc = new XWPFDocument(in)) {
                    text = new XWPFWordExtractor(doc).getText();
                    hasImage = !doc.getAllPictures().isEmpty();
                }
            } else {
                try (HWPFDocument doc = new HWPFDocument(in)) {
                    text = new WordExtractor(doc).getText();
                    hasImage = doc.getPicturesTable() != null && !doc.getPicturesTable().getAllPictures().isEmpty();
                }
            }
            if ((text == null || text.trim().isEmpty()) && !hasImage) {
                log.warn("上传简历: Word 文档为空白, candidateId={}, fileName={}", candidateId, fileName);
                throw new BusinessException(ResumeErrorCode.FILE_EMPTY);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            // 预检失败（损坏/异常格式等）不阻断上传，交由解析链路现有质量校验兜底
            log.warn("上传简历: Word 空白预检异常，按非空白放行, candidateId={}, fileName={}", candidateId, fileName, e);
        }
    }

    /** 是否含嵌入图片（扫描件特征） */
    private boolean hasPdfImages(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ResumeDetailVO getResumeDetail(Long id) {
        Long candidateId = getLoginUserId();
        Resume resume = getOwnedResume(id, candidateId);
        log.info("简历详情查询: resumeId={}, candidateId={}", id, candidateId);
        return toDetailVO(resume);
    }

    @Override
    public void updateResume(Long id, UpdateResumeDTO dto) {
        Long candidateId = getLoginUserId();
        Resume resume = getOwnedResume(id, candidateId);

        Resume update = new Resume();
        update.setId(id);
        update.setCardStructure(cardStructureToJson(dto.getCardStructure()));
        // 联系信息（用户手动填写）
        if (dto.getPhone() != null) {
            update.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            update.setEmail(dto.getEmail());
        }
        if (dto.getWechat() != null) {
            update.setWechat(dto.getWechat());
        }
        if (dto.getCandidateName() != null) {
            update.setCandidateName(dto.getCandidateName());
        }
        resumeMapper.updateById(update);

        log.info("简历在线编辑保存: resumeId={}, candidateId={}", id, candidateId);
    }

    @Override
    @Transactional
    public void deleteResume(Long id) {
        Long candidateId = getLoginUserId();
        Resume resume = getOwnedResume(id, candidateId);

        // 删除的是默认简历则先清默认标记
        if (Integer.valueOf(1).equals(resume.getIsDefault())) {
            resumeMapper.clearDefault(candidateId);
        }
        resumeMapper.deleteById(id);

        // 尽力清理存储原件（失败仅告警，不影响主流程）
        String objectName = storageClient.parseObjectName(resume.getFileUrl());
        if (objectName != null) {
            minioUtil.delete(objectName);
        }

        log.info("删除简历: resumeId={}, candidateId={}", id, candidateId);
    }

    @Override
    public InternalResumeVO getInternalResumeDetail(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ResumeErrorCode.RESUME_NOT_FOUND);
        }
        log.info("内部简历详情查询（精简）: resumeId={}", id);
        return toInternalVO(resume);
    }

    @Override
    public ResumeDetailVO getInternalResumeFullDetail(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ResumeErrorCode.RESUME_NOT_FOUND);
        }
        log.info("内部简历详情查询（全量）: resumeId={}", id);
        return toDetailVO(resume);
    }

    @Override
    public InternalResumeVO getInternalResumeByUserId(Long userId) {
        // 优先返回默认简历，没有则返回最新的一份
        Resume resume = resumeMapper.selectDefaultByCandidateId(userId);
        if (resume == null) {
            List<Resume> resumes = resumeMapper.selectByCandidateId(userId);
            if (resumes != null && !resumes.isEmpty()) {
                resume = resumes.get(0); // 按更新时间倒序，第一个是最新的
            }
        }
        if (resume == null) {
            log.info("用户无简历: userId={}", userId);
            return null;
        }
        log.info("内部简历查询（按用户）: userId={}, resumeId={}", userId, resume.getId());
        return toInternalVO(resume);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Long candidateId = getLoginUserId();
        getOwnedResume(id, candidateId);

        // 先取消该候选人所有默认，再置目标为默认（事务保证互斥）
        resumeMapper.clearDefault(candidateId);
        Resume update = new Resume();
        update.setId(id);
        update.setIsDefault(1);
        resumeMapper.updateById(update);

        log.info("设为默认简历: resumeId={}, candidateId={}", id, candidateId);
    }

    @Override
    public String uploadFacePhoto(Long id, MultipartFile file) {
        Long candidateId = getLoginUserId();
        getOwnedResume(id, candidateId); // 归属校验（不存在/非本人按不存在处理）

        // ① 格式校验（JPG/PNG）
        String format = extractFormat(sanitizeFileName(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (!"jpg".equals(format) && !"jpeg".equals(format) && !"png".equals(format)) {
            throw new BusinessException(ResumeErrorCode.FORMAT_NOT_SUPPORTED.getErrorCode(),
                    "头像仅支持 JPG/PNG 格式");
        }

        // ② 大小校验（≤2MB）
        if (file.getSize() > 2 * 1024 * 1024L) {
            throw new BusinessException(ResumeErrorCode.FILE_TOO_LARGE.getErrorCode(),
                    "头像大小不能超过 2MB");
        }

        // ③ 上传 MinIO（覆盖原头像对象），存 objectName（接口返回时动态签名）
        String objectName = String.format("resumes/%d/face.png", id);
        try (InputStream inputStream = file.getInputStream()) {
            minioUtil.upload(objectName, inputStream, "image/" + ("jpg".equals(format) ? "jpeg" : format));
        } catch (Exception e) {
            log.error("头像上传失败: resumeId={}, object={}", id, objectName, e);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "头像上传失败", e);
        }

        // ④ 落库
        Resume update = new Resume();
        update.setId(id);
        update.setFacePhotoUrl(objectName);
        resumeMapper.updateById(update);

        log.info("头像上传成功: resumeId={}, candidateId={}", id, candidateId);
        return signFacePhotoUrl(objectName);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取当前登录用户ID（未登录抛 401）
     */
    private Long getLoginUserId() {
        Long candidateId = UserContext.getUserId();
        if (candidateId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return candidateId;
    }

    /**
     * 查询当前候选人名下的简历（不存在或非本人所有均按不存在处理，防止越权探测）
     */
    private Resume getOwnedResume(Long id, Long candidateId) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || !resume.getCandidateId().equals(candidateId)) {
            log.warn("简历不存在或无权访问: resumeId={}, candidateId={}", id, candidateId);
            throw new BusinessException(ResumeErrorCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    /**
     * 文件名消毒：去掉路径分隔符，仅保留纯文件名
     */
    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            throw new BusinessException(ResumeErrorCode.FORMAT_NOT_SUPPORTED);
        }
        String name = originalName.replaceAll("\\\\", "/");
        int slash = name.lastIndexOf('/');
        String fileName = slash >= 0 ? name.substring(slash + 1) : name;
        return fileName.trim().isEmpty() ? "resume" : fileName;
    }

    /**
     * 提取小写扩展名（含点前缀的文件名视为无扩展名）
     */
    private String extractFormat(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 格式 → Content-Type 映射
     */
    private String contentTypeOf(String format) {
        switch (format) {
            case "pdf":
                return "application/pdf";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":
                return "application/msword";
            case "jpg":
                return "image/jpeg";
            case "png":
                return "image/png";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 更新上传进度缓存（Hash {step, percentage}，TTL 10min）
     */
    private void updateUploadProgress(String key, String step, int percentage) {
        try {
            redisTemplate.opsForHash().put(key, "step", step);
            redisTemplate.opsForHash().put(key, "percentage", percentage);
            redisTemplate.expire(key, UPLOAD_PROGRESS_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 进度缓存失败不影响上传主流程
            log.warn("上传进度缓存写入失败: key={}", key, e);
        }
    }

    /**
     * cardStructure JSON 节点转存储字符串（兼容前端传 JSON 对象或 JSON 字符串）
     */
    private String cardStructureToJson(JsonNode node) {
        if (node.isTextual()) {
            return node.textValue();
        }
        return node.toString();
    }


    private ResumeVO toListVO(Resume resume) {
        ResumeVO vo = new ResumeVO();
        vo.setId(resume.getId());
        vo.setFileName(resume.getFileName());
        vo.setFileFormat(resume.getFileFormat());
        vo.setIsDefault(resume.getIsDefault());
        vo.setParseStatus(resume.getParseStatus());
        vo.setCreatedAt(resume.getCreatedAt());
        return vo;
    }

    /**
     * Entity → InternalResumeVO（内部接口精简视图，不含联系方式等敏感字段）
     */
    private InternalResumeVO toInternalVO(Resume resume) {
        InternalResumeVO vo = new InternalResumeVO();
        vo.setId(resume.getId());
        vo.setParseStatus(resume.getParseStatus());
        vo.setCardStructure(parseCardStructure(resume.getCardStructure()));
        vo.setResumeMdUrl(resume.getResumeMdUrl());
        vo.setCandidateName(resume.getCandidateName());
        return vo;
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId(resume.getId());
        vo.setFileName(resume.getFileName());
        vo.setFileFormat(resume.getFileFormat());
        vo.setFileSize(resume.getFileSize());
        vo.setIsDefault(resume.getIsDefault());
        vo.setParseStatus(resume.getParseStatus());
        vo.setResumeMdUrl(resume.getResumeMdUrl());
        vo.setFacePhotoUrl(signFacePhotoUrl(resume.getFacePhotoUrl()));
        vo.setCardStructure(parseCardStructure(resume.getCardStructure()));
        vo.setPhone(resume.getPhone());
        vo.setEmail(resume.getEmail());
        vo.setWechat(resume.getWechat());
        vo.setCandidateName(resume.getCandidateName());
        vo.setCreatedAt(resume.getCreatedAt());
        vo.setUpdatedAt(resume.getUpdatedAt());
        return vo;
    }

    /**
     * 头像 URL 动态签名：DB 存 objectName（resumes/{id}/face.png），返回时生成 7 天 presigned URL，
     * 保证前端每次请求都拿到新签名（永不过期）。
     * <p>兼容旧数据：历史记录可能存的是 presigned URL 形态，自动提取 objectName 重新签名。
     */
    private String signFacePhotoUrl(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        // 旧数据兼容：URL 形态 → 提取 objectName；纯 objectName 原样使用
        String objectName = stored;
        String parsed = storageClient.parseObjectName(stored);
        if (parsed != null) {
            objectName = parsed;
        }
        String url = storageClient.presignedUrl(objectName);
        if (url == null) {
            log.error("头像 presigned URL 生成失败: object={}", objectName);
        }
        return url;
    }

    /**
     * cardStructure 字符串解析为 JSON 对象返回（解析失败按原字符串返回，避免影响主流程）
     */
    private Object parseCardStructure(String cardStructure) {
        if (cardStructure == null || cardStructure.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(cardStructure);
        } catch (JsonProcessingException e) {
            log.warn("cardStructure 解析失败，按原字符串返回: {}", cardStructure);
            return cardStructure;
        }
    }
}

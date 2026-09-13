package com.lingxi.resume.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingxi.common.constant.RedisKeyConstant;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.MinioUtil;
import com.lingxi.common.util.SecurityUtil;
import com.lingxi.resume.config.ResumeStorageClient;
import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.domain.entity.ResumeAbilityModel;
import com.lingxi.resume.domain.vo.AbilityModelVO;
import com.lingxi.resume.domain.vo.ParseTaskStatusVO;
import com.lingxi.resume.exception.ResumeErrorCode;
import com.lingxi.resume.mapper.ResumeAbilityModelMapper;
import com.lingxi.resume.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简历解析编排服务
 *
 * <p>解析状态机：PENDING → PARSING → COMPLETED / FAILED（FAILED 保留记录，用户手动删除）。
 * <ul>
 *   <li>{@link #parseAsync} —— 上传/兜底扫描入口（{@code @Async("resumeParseExecutor")}）</li>
 *   <li>{@link #subscribeAndWait} —— SSE 端点订阅入口（已完成直接推历史，进行中订阅进度）</li>
 *   <li>{@link #getTaskStatus} —— Redis 任务状态查询（跳页后回页轮询的完成信号）</li>
 *   <li>重试：Agent 异常重试 1 次，仍失败 → {@link RuleParseFallback} 降级产出默认结构</li>
 *   <li>隐私：产出落库前统一脱敏（手机号/身份证）</li>
 * </ul>
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /** resume.md 存储 objectName 模板 */
    private static final String MD_OBJECT_TEMPLATE = "resumes/%d/resume.md";

    /** 解析任务状态 TTL（毫秒）：RUNNING/COMPLETED 10min（覆盖一次完整解析 + 回页感知窗口） */
    private static final long TASK_TTL_MS = 10 * 60 * 1000L;

    /** 解析任务状态 TTL（毫秒）：FAILED 5min（短暂展示失败即可，DB 状态是持久真源） */
    private static final long TASK_FAILED_TTL_MS = 5 * 60 * 1000L;

    /** 个人信息类章节标题：与独立列（name/phone/email/wechat）重复，清洗重复要点 */
    private static final Set<String> PERSONAL_SECTION_TITLES = new HashSet<>(Arrays.asList(
            "基本信息", "个人信息", "基本资料", "个人资料", "联系方式"));

    /** 进行中的解析任务标记：resumeId → true（防重复触发） */
    private final ConcurrentHashMap<Long, AtomicBoolean> parsingFlags = new ConcurrentHashMap<>();

    /** SSE 订阅列表：resumeId → emitters */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    private final ResumeMapper resumeMapper;
    private final ResumeAbilityModelMapper abilityModelMapper;
    private final ResumeAgentService resumeAgentService;
    private final BaibaoxiangClient baibaoxiangClient;
    private final FacePhotoExtractor facePhotoExtractor;
    private final RuleParseFallback ruleParseFallback;
    private final MinioUtil minioUtil;
    private final ResumeStorageClient storageClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final org.springframework.context.ApplicationContext applicationContext;

    // ==================== 入口 ====================

    /**
     * 异步触发解析（经 Spring 代理调用，保证 @Async 生效）
     *
     * <p>注：不能直接 this.parseAsync()——自调用绕过 AOP 代理导致 @Async 失效，
     * 解析会在当前线程同步执行，SSE 订阅者来不及注册。
     */
    private void triggerParse(Long resumeId) {
        applicationContext.getBean(ResumeParseService.class).parseAsync(resumeId);
    }

    /**
     * 异步解析入口（上传/兜底扫描调用）。异常仅记录，不向外抛（异步线程无请求上下文）
     */
    @Async("resumeParseExecutor")
    public void parseAsync(Long resumeId) {
        try {
            doParse(resumeId);
        } catch (AgentCancelledException e) {
            log.info("解析被取消: resumeId={}", resumeId);
        } catch (Exception e) {
            log.error("异步解析异常: resumeId={}", resumeId, e);
        }
    }

    /**
     * 同步解析（内部执行链路），重入安全：条件 UPDATE 防并发
     */
    public void doParse(Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            log.warn("解析目标不存在或已删除: resumeId={}", resumeId);
            return;
        }
        // 条件流转 PENDING→PARSING（rows=0：已被并发解析/状态已变更，跳过）
        if (resumeMapper.updateParseStatus(resumeId, STATUS_PENDING, STATUS_PARSING) == 0) {
            log.info("跳过解析（状态已变更）: resumeId={}, current={}", resumeId, resume.getParseStatus());
            return;
        }
        parsingFlags.computeIfAbsent(resumeId, k -> new AtomicBoolean(true)).set(true);
        setTaskRunning(resumeId);
        log.info("开始解析简历: resumeId={}", resumeId);
        try {
            // ① 生成 PDF 的 presigned URL（桶私有，LLM/百宝箱需带签名访问）
            String objectName = storageClient.parseObjectName(resume.getFileUrl());
            String pdfUrl = storageClient.presignedUrl(objectName);
            if (pdfUrl == null) {
                throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "简历文件不可访问，无法解析");
            }
            // ①.5 提取头像（失败不影响主流程，无头像保持 null）
            // face_photo_url 存 objectName（resumes/{id}/face.png），接口返回时动态生成 presigned URL
            try {
                String faceObjectName = facePhotoExtractor.extractAndUpload(objectName, resume.getId());
                if (faceObjectName != null) {
                    Resume faceUpdate = new Resume();
                    faceUpdate.setId(resume.getId());
                    faceUpdate.setFacePhotoUrl(faceObjectName);
                    resumeMapper.updateById(faceUpdate);
                    log.info("头像提取成功: resumeId={}", resume.getId());
                } else {
                    log.info("头像提取: 无合适候选图片, resumeId={}", resume.getId());
                }
            } catch (Exception e) {
                log.warn("头像提取异常（跳过，不影响解析主流程）: resumeId={}", resume.getId(), e);
            }
            // ② 两阶段解析（2026-08-12 恢复）：单次全量输出超长触发平台 90s 网关切断（慢+不稳定），
            // 且全量指令无结构样例导致"张冠李戴/编造"；两阶段每阶段短输出、指令自带严格样例
            // 阶段1 CARD：只出 card_structure（短输出，90s 内完成）→ 落库 + SSE:final 前端渲染卡片
            AgentResult cardResult = runAgentWithFallback(resumeId, pdfUrl, BaibaoxiangClient.MODE_CARD);
            // 合并同标题 sections（防止重复"基本信息"等）——落库与 final 共用合并后的结果
            cardResult.setCardStructure(mergeDuplicateSections(cardResult.getCardStructure()));
            persistCardStructure(resume, cardResult);
            broadcast(resumeId, "final", finalEvent(resumeId, cardResult));
            log.info("简历卡片解析完成（阶段1 CARD）: resumeId={}", resumeId);
            // 阶段2 SCORE：复用 CARD 会话（不重发简历文本）只出 personal+ability_model → 后台落库
            // listener 传空监听器：final 已推送，SCORE 的 thinking/progress 不再广播给前端
            try {
                AgentResult scoreResult = runAgentWithFallback(resumeId, pdfUrl, BaibaoxiangClient.MODE_SCORE,
                        (event, data) -> { });
                persistScoreAndMd(resume, cardResult, scoreResult);
            } catch (Exception e) {
                // SCORE 失败降级（2026-08-12 决策）：卡片已落库已渲染，personal/能力模型留空，
                // 不推 error、不置 FAILED——用户已拿到卡片，评分可后续重传/重新解析补齐
                log.error("阶段2 SCORE 失败，降级 COMPLETED（卡片已可用）: resumeId={}", resumeId, e);
            }
            // ⑥ 置 COMPLETED（两阶段都完成，或 SCORE 降级后卡片有效即完成）
            markCompleted(resume.getId());
            setTaskCompleted(resumeId);
            log.info("简历解析完成（阶段2 SCORE）: resumeId={}", resumeId);
        } catch (AgentCancelledException e) {
            // 客户端断开等取消：不置 FAILED，仅清理
            throw e;
        } catch (Exception e) {
            log.error("简历解析失败: resumeId={}", resumeId, e);
            markFailed(resumeId);
            setTaskFailed(resumeId, e.getMessage());
            broadcast(resumeId, "error", errorEvent(resumeId, e));
        } finally {
            parsingFlags.remove(resumeId);
            completeSubscribers(resumeId);
        }
    }

    // ==================== SSE 订阅 ====================

    /**
     * SSE 端点订阅入口：
     * <ul>
     *   <li>不存在/已删除 → SSE:error(3101)</li>
     *   <li>COMPLETED → 直接推 final（历史结果，不重新解析）</li>
     *   <li>FAILED → 推 error(3502)</li>
     *   <li>PENDING/PARSING → 注册订阅；PENDING 且无进行中任务则触发解析</li>
     * </ul>
     */
    public void subscribeAndWait(Long resumeId, SseEmitter emitter) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            sendError(emitter, ResumeErrorCode.RESUME_NOT_FOUND.getErrorCode(),
                    ResumeErrorCode.RESUME_NOT_FOUND.getErrorMessage());
            emitter.complete();
            return;
        }
        if (STATUS_COMPLETED.equals(resume.getParseStatus())) {
            // 已完成：推历史结果
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("parse_status", STATUS_COMPLETED);
            event.put("card_structure", parseCardStructure(resume.getCardStructure()));
            sendEvent(emitter, "final", event);
            emitter.complete();
            return;
        }
        if (STATUS_FAILED.equals(resume.getParseStatus())) {
            sendError(emitter, ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                    ResumeErrorCode.PARSE_FAILED.getErrorMessage());
            emitter.complete();
            return;
        }
        // PENDING/PARSING：注册订阅；PENDING 且无进行中任务 → 触发解析
        AtomicBoolean running = parsingFlags.get(resumeId);
        if ((running == null || !running.get()) && STATUS_PENDING.equals(resume.getParseStatus())) {
            triggerParse(resumeId);
        }
        registerSubscriber(resumeId, emitter);
    }

    /**
     * 客户端断开回调（移除订阅，不取消解析本身，落库照常）
     */
    public void onDisconnect(Long resumeId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(resumeId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    /**
     * 能力模型查询（含归属校验：仅本人简历）
     */
    public AbilityModelVO getAbilityModel(Long resumeId) {
        Long candidateId = getLoginUserId();
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !resume.getCandidateId().equals(candidateId)) {
            throw new BusinessException(ResumeErrorCode.RESUME_NOT_FOUND);
        }
        ResumeAbilityModel model = abilityModelMapper.selectByResumeId(resumeId);
        if (model == null) {
            return null;
        }
        AbilityModelVO vo = new AbilityModelVO();
        vo.setResumeId(model.getResumeId());
        vo.setProfessionalSkillScore(model.getProfessionalSkillScore());
        vo.setWorkExperienceScore(model.getWorkExperienceScore());
        vo.setIndustryKnowledgeScore(model.getIndustryKnowledgeScore());
        vo.setComprehensiveQualityScore(model.getComprehensiveQualityScore());
        vo.setLearningGrowthScore(model.getLearningGrowthScore());
        vo.setSubDimensions(parseJson(model.getSubDimensions()));
        vo.setUpdatedAt(model.getUpdatedAt());
        return vo;
    }

    // ==================== 解析链路 ====================

    /**
     * Agent 循环 + 重试 1 次 + 规则降级（全量模式）
     */
    private AgentResult runAgentWithFallback(Long resumeId, String pdfUrl) {
        return runAgentWithFallback(resumeId, pdfUrl, null);
    }

    /**
     * Agent 循环 + 重试 1 次（按模式：MODE_CARD / MODE_SCORE / null=全量）
     */
    private AgentResult runAgentWithFallback(Long resumeId, String pdfUrl, String mode) {
        return runAgentWithFallback(resumeId, pdfUrl, mode, null);
    }

    /**
     * Agent 循环 + 重试 1 次（按模式），可指定事件监听
     *
     * @param listener null=默认 broadcast 给 SSE 订阅者（CARD 阶段）；SCORE 阶段传
     *                 空监听器 {@code (e, d) -> {}}，避免 final 后仍向前端推 thinking/progress
     */
    private AgentResult runAgentWithFallback(Long resumeId, String pdfUrl, String mode,
                                             ResumeAgentService.AgentEventListener listener) {
        if (listener == null) {
            listener = (event, data) -> broadcast(resumeId, event, data);
        }
        try {
            return resumeAgentService.run(pdfUrl, mode, resumeId, listener, new AtomicBoolean(false));
        } catch (AgentCancelledException e) {
            throw e;
        } catch (Exception first) {
            log.warn("Agent 解析失败，重试 1 次: resumeId={}, mode={}", resumeId, mode, first);
            try {
                return resumeAgentService.run(pdfUrl, mode, resumeId, listener, new AtomicBoolean(false));
            } catch (AgentCancelledException e) {
                throw e;
            } catch (Exception second) {
                // 内容不合格（空 PDF/无法提取）不降级——FAILED + 逻辑删除由上层处理
                log.error("Agent 解析重试仍失败，置 FAILED: resumeId={}, mode={}", resumeId, mode, second);
                if (second instanceof BusinessException) {
                    throw (BusinessException) second;
                }
                throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(),
                        "简历解析失败", second);
            }
        }
    }

    /**
     * card_structure 后处理（保证落库字段干净，其他模块直接消费无重复）：
     * <ol>
     *   <li>合并同标题 sections（防止重复章节，如两个"基本信息"），points 拼接</li>
     *   <li>清洗个人信息类 section：移除与独立列重复的要点（姓名/电话/邮箱/微信），
     *       保留求职意向/年龄/地址等独立列没有的信息</li>
     * </ol>
     * <p>覆盖所有来源：LLM 输出 / 规则引擎降级 / Mock；空或异常结构原样返回。
     */
    private JsonNode mergeDuplicateSections(JsonNode cardStructure) {
        if (cardStructure == null || !cardStructure.has("sections")
                || !cardStructure.path("sections").isArray()) {
            return cardStructure;
        }
        ObjectNode root = (ObjectNode) cardStructure;
        ArrayNode merged = objectMapper.createArrayNode();
        Map<String, ArrayNode> byTitle = new LinkedHashMap<>();
        for (JsonNode section : root.path("sections")) {
            String title = section.path("title").asText("").trim();
            ArrayNode points = byTitle.get(title);
            if (points == null) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("title", title);
                // 保留置信度与原始标题（合并取首个 section 的值）
                if (section.has("confidence")) {
                    node.put("confidence", section.get("confidence").asText());
                }
                if (section.has("raw_text")) {
                    node.put("raw_text", section.get("raw_text").asText());
                }
                node.set("points", objectMapper.createArrayNode());
                points = (ArrayNode) node.get("points");
                byTitle.put(title, points);
                merged.add(node);
            }
            // 个人信息类 section：移除与独立列重复的要点（姓名/电话/邮箱/微信），
            // 保留求职意向/年龄/地址等独立列没有的信息
            boolean isPersonal = PERSONAL_SECTION_TITLES.contains(title);
            for (JsonNode point : section.path("points")) {
                if (isPersonal && isPersonalDuplicatePoint(point.path("text").asText())) {
                    continue;
                }
                points.add(point);
            }
        }
        root.set("sections", merged);
        return root;
    }

    /** 个人信息类要点判定：含姓名/电话/手机/邮箱/微信等关键字（与独立列重复，清洗） */
    private boolean isPersonalDuplicatePoint(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches(".*(姓名|电话|手机|邮箱|微信|email|phone|wechat).*");
    }

    /**
     * 阶段1落库：仅写 card_structure（不置 COMPLETED，评分在后台）
     */
    private void persistCardStructure(Resume resume, AgentResult cardResult) {
        String cardJson = cardResult.getCardStructure() == null ? "{}" : cardResult.getCardStructure().toString();
        cardJson = sanitizeText(cardJson);
        Resume update = new Resume();
        update.setId(resume.getId());
        update.setCardStructure(cardJson);
        resumeMapper.updateById(update);
        log.info("卡片结构已落库: resumeId={}", resume.getId());
    }

    /**
     * 阶段2落库：resume.md → MinIO、个人信息独立列、能力模型
     * cardResult 为阶段1产物（card_structure 已入库，这里合并 score 阶段产物）
     */
    private void persistScoreAndMd(Resume resume, AgentResult cardResult, AgentResult scoreResult) {
        // ① resume.md → MinIO（桶默认私有，落库用 7 天预签名 URL，保证可直接访问）
        String mdObjectName = String.format(MD_OBJECT_TEMPLATE, resume.getId());
        // LLM 不产出 resume_md（两阶段模式）→ 用 Tika 原文兜底
        String llmMd = scoreResult.getResumeMd();
        String tikaText = baibaoxiangClient.getLastResumeText(resume.getId().toString());
        String mdContent = (llmMd != null && !llmMd.isEmpty()) ? sanitizeText(llmMd)
                : (tikaText != null ? sanitizeText(tikaText) : "");
        if (mdContent.isEmpty()) {
            log.warn("resume.md 内容为空（无 LLM 产物且无 Tika 文本）: resumeId={}", resume.getId());
        }
        String mdUrl;
        try (ByteArrayInputStream mdStream =
                     new ByteArrayInputStream(mdContent.getBytes(StandardCharsets.UTF_8))) {
            mdUrl = minioUtil.upload(mdObjectName, mdStream, "text/markdown");
            // 桶私有，落库用 presigned URL 保证可访问
            String presigned = storageClient.presignedUrl(mdObjectName);
            if (presigned != null) {
                mdUrl = presigned;
            }
        } catch (Exception e) {
            log.error("resume.md 上传 MinIO 失败: resumeId={}", resume.getId(), e);
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "解析结果保存失败", e);
        }

        // ② 个人信息独立列 + resume_md_url
        Resume update = new Resume();
        update.setId(resume.getId());
        update.setResumeMdUrl(mdUrl);
        update.setCandidateName(scoreResult.getName());
        update.setPhone(scoreResult.getPhone());
        update.setEmail(scoreResult.getEmail());
        update.setWechat(scoreResult.getWechat());
        resumeMapper.updateById(update);

        // ③ 能力模型：按 resume_id 唯一，存在则覆盖更新
        ResumeAbilityModel abilityModel = new ResumeAbilityModel();
        abilityModel.setCandidateId(resume.getCandidateId());
        abilityModel.setResumeId(resume.getId());
        abilityModel.setProfessionalSkillScore(scoreResult.getProfessionalSkillScore());
        abilityModel.setWorkExperienceScore(scoreResult.getWorkExperienceScore());
        abilityModel.setIndustryKnowledgeScore(scoreResult.getIndustryKnowledgeScore());
        abilityModel.setComprehensiveQualityScore(scoreResult.getComprehensiveQualityScore());
        abilityModel.setLearningGrowthScore(scoreResult.getLearningGrowthScore());
        abilityModel.setSubDimensions(sanitizeJsonField(scoreResult.getSubDimensions()));
        if (abilityModelMapper.selectByResumeId(resume.getId()) != null) {
            abilityModelMapper.updateByResumeId(abilityModel);
        } else {
            abilityModelMapper.insert(abilityModel);
        }
    }

    /**
     * 置 COMPLETED（两阶段都完成）
     */
    private void markCompleted(Long resumeId) {
        Resume update = new Resume();
        update.setId(resumeId);
        update.setParseStatus(STATUS_COMPLETED);
        resumeMapper.updateById(update);
    }

    /**
     * 标记解析失败（保留记录，不自动删除——2026-08-09 决策：失败由用户手动删除，
     * 避免解析失败时简历无声消失；失败记录占 5 份名额，删除后释放）
     */
    private void markFailed(Long resumeId) {
        try {
            Resume update = new Resume();
            update.setId(resumeId);
            update.setParseStatus(STATUS_FAILED);
            resumeMapper.updateById(update);
            log.warn("简历解析失败，已置 FAILED（保留记录，可手动删除）: resumeId={}", resumeId);
        } catch (Exception e) {
            log.error("标记解析失败异常: resumeId={}", resumeId, e);
        }
    }

    // ==================== 任务状态（Redis，异步化轮询用） ====================

    private String taskKey(Long resumeId) {
        return RedisKeyConstant.format(RedisKeyConstant.PARSE_TASK, resumeId);
    }

    /**
     * 解析任务状态查询（RUNNING/COMPLETED/FAILED/NONE）
     *
     * <p>状态是进程内生命周期数据（TTL 过期即 NONE），DB 的 parseStatus 才是持久真源；
     * 前端轮询到 COMPLETED/FAILED/NONE 后停轮询，刷新列表以 DB 为准展示。
     */
    public ParseTaskStatusVO getTaskStatus(Long resumeId) {
        ParseTaskStatusVO vo = new ParseTaskStatusVO();
        vo.setState("NONE");
        try {
            Object value = redisTemplate.opsForValue().get(taskKey(resumeId));
            if (value == null) {
                return vo;
            }
            String raw = value.toString();
            if ("RUNNING".equals(raw)) {
                vo.setState("RUNNING");
            } else if ("COMPLETED".equals(raw)) {
                // 解析无 reportId 概念（完成即 DB 落库），值直接为 COMPLETED
                vo.setState("COMPLETED");
            } else if (raw.startsWith("FAILED:")) {
                vo.setState("FAILED");
            }
        } catch (Exception e) {
            // Redis 异常 fail-open：返回 NONE，前端停止轮询，不阻塞页面
            log.warn("解析任务状态查询失败(返回NONE): resumeId={}", resumeId, e);
        }
        return vo;
    }

    private void setTaskRunning(Long resumeId) {
        try {
            redisTemplate.opsForValue().set(taskKey(resumeId), "RUNNING",
                    TASK_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("解析任务状态写入失败(fail-open): resumeId={}", resumeId, e);
        }
    }

    private void setTaskCompleted(Long resumeId) {
        try {
            redisTemplate.opsForValue().set(taskKey(resumeId), "COMPLETED",
                    TASK_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("解析任务状态写入失败(fail-open): resumeId={}", resumeId, e);
        }
    }

    private void setTaskFailed(Long resumeId, String message) {
        try {
            String msg = (message == null || message.trim().isEmpty()) ? "简历解析失败" : message;
            // 截断：FAILED 值会经 JSON 序列化返回前端，超长错误栈没有意义
            if (msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            redisTemplate.opsForValue().set(taskKey(resumeId), "FAILED:" + msg,
                    TASK_FAILED_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("解析任务状态写入失败(fail-open): resumeId={}", resumeId, e);
        }
    }

    /**
     * 启动清理：删除全部解析任务状态 key（RUNNING/COMPLETED/FAILED 均为进程内生命周期数据）。
     *
     * <p>进程异常退出时 RUNNING 状态会悬挂到 TTL 过期，期间前端轮询一直显示"解析中"。
     * 启动时全量清除，保证新进程从干净状态开始。
     *
     * <p>注意：多实例部署时不应开启（会误删其他实例在跑的任务）；当前单机部署无影响。
     *
     * <p>实现说明：不能用 implements ApplicationRunner——本类含 @Async 方法且无接口，
     * 一旦实现接口 Spring 会改用 JDK 动态代理（仅暴露接口方法），类自身的注入点
     * （Controller/Service/getBean(ResumeParseService.class)）全部失效。事件监听无此问题。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupStaleTaskStates() {
        try {
            Set<String> keys = redisTemplate.keys("parse:task:*");
            if (keys != null && !keys.isEmpty()) {
                long removed = redisTemplate.delete(keys);
                log.info("启动清理残留解析任务状态: 删除 {} 个 key", removed);
            }
        } catch (Exception e) {
            log.warn("启动清理解析任务状态失败（不影响启动）: {}", e.getMessage());
        }
    }

    // ==================== SSE 工具方法 ====================

    private void registerSubscriber(Long resumeId, SseEmitter emitter) {
        subscribers.computeIfAbsent(resumeId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> onDisconnect(resumeId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            onDisconnect(resumeId, emitter);
        });
        emitter.onError(t -> onDisconnect(resumeId, emitter));
    }

    /**
     * 广播事件给该简历的所有订阅者（订阅者断开时静默移除）
     */
    private void broadcast(Long resumeId, String event, Object data) {
        List<SseEmitter> list = subscribers.get(resumeId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            sendEvent(emitter, event, data);
        }
    }

    /**
     * 解析结束：清空订阅列表并 complete 所有 emitter
     */
    private void completeSubscribers(Long resumeId) {
        List<SseEmitter> list = subscribers.remove(resumeId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE emitter 关闭失败: resumeId={}", resumeId, e);
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            // 客户端已断开：移除此订阅
            log.debug("SSE 事件发送失败: event={}", event, e);
            emitter.completeWithError(e);
        }
    }

    private void sendError(SseEmitter emitter, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        sendEvent(emitter, "error", error);
    }

    private Map<String, Object> finalEvent(Long resumeId, AgentResult result) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("parse_status", STATUS_COMPLETED);
        // SSE 事件同样脱敏（前端可能直接渲染 final 数据，避免明文泄露）
        String cardJson = sanitizeText(
                result.getCardStructure() == null ? "{}" : result.getCardStructure().toString());
        try {
            event.put("card_structure", objectMapper.readTree(cardJson));
        } catch (Exception e) {
            log.warn("final 事件 card_structure 解析失败，回退原始节点: resumeId={}", resumeId);
            event.put("card_structure", result.getCardStructure());
        }
        return event;
    }

    private Map<String, Object> errorEvent(Long resumeId, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", ResumeErrorCode.PARSE_FAILED.getErrorCode());
        error.put("message", e.getMessage() == null ? "简历解析失败" : e.getMessage());
        return error;
    }

    // ==================== 通用工具 ====================

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
     * 清理 JSON 字段值：LLM 可能输出非对象值（如字符串），MySQL JSON 列只接受 {} 或 []
     */
    static String sanitizeJsonField(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "{}";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        return "{}";
    }

    /**
     * 文本级隐私脱敏：替换大文本中的手机号/身份证为掩码
     *
     * <p>注：{@link SecurityUtil#maskPhone}/{@link SecurityUtil#maskIdCard} 是单字段脱敏
     * （按字符串长度截取），不适用于整段文本/JSON；此处用正则仅替换文本中的号码模式，
     * 不破坏 JSON 结构与其余内容。
     */
    public static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 手机号：1[3-9] 开头 11 位 → 1**********
        text = text.replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "1**********");
        // 身份证：18 位（17 数字 + 数字/X，含日期结构）→ **********
        text = text.replaceAll("(?<!\\d)[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])"
                + "(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx](?!\\d)", "**********");
        return text;
    }

    /**
     * JSON 字符串解析为对象（解析失败按原字符串返回，不影响主流程）
     */
    private Object parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }

    private Object parseCardStructure(String cardStructure) {
        return parseJson(cardStructure);
    }

    /**
     * 生成 MinIO 对象 7 天有效 presigned URL（桶私有，需签名访问）
     */
}

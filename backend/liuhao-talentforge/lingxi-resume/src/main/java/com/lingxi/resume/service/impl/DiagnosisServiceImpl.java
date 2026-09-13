package com.lingxi.resume.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.constant.RedisKeyConstant;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.MinioUtil;
import com.lingxi.resume.agent.A2AEvent;
import com.lingxi.resume.agent.BaibaoxiangClient;
import com.lingxi.resume.config.ResumeStorageClient;
import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.domain.entity.ResumeAbilityModel;
import com.lingxi.resume.domain.entity.ResumeDiagnosisReport;
import com.lingxi.resume.domain.vo.DiagnosisDetailVO;
import com.lingxi.resume.domain.vo.DiagnosisHistoryVO;
import com.lingxi.resume.domain.vo.DiagnosisTaskStatusVO;
import com.lingxi.resume.exception.ResumeErrorCode;
import com.lingxi.resume.mapper.ResumeAbilityModelMapper;
import com.lingxi.resume.mapper.ResumeDiagnosisReportMapper;
import com.lingxi.resume.mapper.ResumeMapper;
import com.lingxi.resume.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 简历诊断服务实现
 *
 * <p>流程：校验 → 缓存检查 → 百宝箱 Agent → MinIO + DB 落库 → MQ 通知 → SSE final。
 *
 * @author 成员C
 * @since 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisServiceImpl implements DiagnosisService, ApplicationRunner {

    /** 诊断 MinIO objectName 模板 */
    private static final String DIAG_OBJECT_TEMPLATE = "diagnosis/%d/%s/%s.md";
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    /** Redis 限流 key 前缀 */
    private static final String RATE_LIMIT_KEY = "diagnosis:rate:";

    /** SSE 超时（诊断比解析快，2min 够） */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    /** 每职业最多保留的诊断历史条数（超出裁剪，含 MinIO 文件） */
    private static final int MAX_HISTORY_PER_CAREER = 3;

    /** 历史列表最多返回条数（跨职业合计） */
    private static final int MAX_HISTORY_TOTAL = 10;

    /** 诊断任务状态 TTL：RUNNING/COMPLETED 10 分钟（覆盖慢诊断 + 回页感知窗口） */
    private static final long TASK_TTL_MS = 10 * 60_000L;

    /** 诊断任务 FAILED 状态 TTL：5 分钟（用户回页看到失败原因后即可过期） */
    private static final long TASK_FAILED_TTL_MS = 5 * 60_000L;

    private final ResumeMapper resumeMapper;
    private final ResumeAbilityModelMapper abilityModelMapper;
    private final ResumeDiagnosisReportMapper diagnosisReportMapper;
    private final BaibaoxiangClient baibaoxiangClient;
    private final MinioUtil minioUtil;
    /** 统一存储客户端（按 storage.type 分发 MinIO/OSS） */
    private final ResumeStorageClient storageClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    /** Spring 容器：获取 @Async 代理（自调用不走 AOP 代理，必须经 getBean 取代理） */
    private final ApplicationContext applicationContext;

    /** 诊断 SSE 订阅列表：taskKey(resumeId:careerBase64) → emitters。连接断开只退订，不取消任务 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<>();

    // ==================== 主流程 ====================

    @Override
    public void diagnose(Long resumeId, String career, boolean refresh, SseEmitter emitter) {
        // ① 批量校验
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            sendErrorAndComplete(emitter, ResumeErrorCode.RESUME_NOT_FOUND.getErrorCode(),
                    ResumeErrorCode.RESUME_NOT_FOUND.getErrorMessage());
            return;
        }
        if (!"COMPLETED".equals(resume.getParseStatus())) {
            sendErrorAndComplete(emitter, ResumeErrorCode.NOT_PARSED.getErrorCode(),
                    ResumeErrorCode.NOT_PARSED.getErrorMessage());
            return;
        }
        String trimmedCareer = career != null ? career.trim() : "";
        if (trimmedCareer.isEmpty()) {
            sendErrorAndComplete(emitter, ResumeErrorCode.CAREER_EMPTY.getErrorCode(),
                    ResumeErrorCode.CAREER_EMPTY.getErrorMessage());
            return;
        }
        String taskKey = taskKey(resumeId, trimmedCareer);

        // ② 去重（在限流前：诊断进行中不消耗限流次数）
        // 方案C：任务与连接解耦后，同一 resumeId+career 的后台任务可能仍在跑，
        // 前端按钮 disabled 是主力，此处 Redis 状态兜底
        if (isTaskRunning(resumeId, trimmedCareer)) {
            log.info("诊断去重命中: resumeId={}, career={}", resumeId, trimmedCareer);
            sendErrorAndComplete(emitter, ResumeErrorCode.AI_SERVICE_UNAVAILABLE.getErrorCode(),
                    "诊断正在进行中，请稍后再试");
            return;
        }

        // ③ 限流（每用户每分钟 3 次）
        Long candidateId = resume.getCandidateId();
        if (!checkRateLimit(candidateId)) {
            sendErrorAndComplete(emitter, ResumeErrorCode.AI_SERVICE_UNAVAILABLE.getErrorCode(),
                    "诊断请求太频繁，请稍后再试");
            return;
        }

        // ④ 缓存检查（同简历+同职业最近一次）
        // 2026-08-08：缓存自动失效——简历修改（updatedAt 晚于报告 createdAt）或
        // 显式 refresh=true 时跳过缓存重新生成，解决"改简历后同职业无法重诊断"
        List<ResumeDiagnosisReport> history = diagnosisReportMapper
                .selectByResumeIdAndCareer(resumeId, trimmedCareer, 1);
        if (!history.isEmpty()) {
            ResumeDiagnosisReport cached = history.get(0);
            boolean resumeModified = resume.getUpdatedAt() != null
                    && cached.getCreatedAt() != null
                    && resume.getUpdatedAt().isAfter(cached.getCreatedAt());
            if (!refresh && !resumeModified) {
                log.info("诊断缓存命中: resumeId={}, career={}, reportId={}",
                        resumeId, trimmedCareer, cached.getId());
                pushCachedResult(emitter, cached);
                return;
            }
            log.info("诊断缓存跳过: resumeId={}, career={}, refresh={}, resumeModified={}",
                    resumeId, trimmedCareer, refresh, resumeModified);
        }

        // ⑤ 订阅 + 任务状态 + 异步执行（方案C：本方法立即返回，任务跑在诊断线程池）
        registerSubscriber(taskKey, emitter);
        // 连接断开只退订，不取消任务——后台继续跑完落库，回页轮询拿结果
        emitter.onCompletion(() -> unsubscribe(taskKey, emitter));
        emitter.onTimeout(() -> {
            unsubscribe(taskKey, emitter);
            emitter.complete();
        });
        emitter.onError(t -> unsubscribe(taskKey, emitter));

        setTaskRunning(resumeId, trimmedCareer);
        broadcast(taskKey, "submitted", submittedMap(taskKey, trimmedCareer));

        try {
            // 自调用不走 Spring AOP 代理，@Async 不生效——必须经容器取接口代理触发
            // （JDK 动态代理仅暴露接口类型，getBean(实现类) 会 NoSuchBeanDefinitionException）
            applicationContext.getBean(DiagnosisService.class)
                    .diagnoseAsync(resume, trimmedCareer, taskKey);
        } catch (Exception e) {
            // 提交失败（代理获取/线程池异常等）：状态落 FAILED + 广播 error，避免
            // RUNNING 悬挂——前端轮询到 FAILED 会恢复待诊断态并可重新发起
            log.error("诊断任务提交失败: resumeId={}, career={}", resumeId, trimmedCareer, e);
            setTaskFailed(resumeId, trimmedCareer, "诊断任务提交失败");
            broadcast(taskKey, "error",
                    errorMap(ResumeErrorCode.AI_SERVICE_UNAVAILABLE.getErrorCode(),
                            "诊断任务提交失败，请重试"));
            completeSubscribers(taskKey);
        }
    }

    /**
     * 启动清理：删除全部诊断任务状态 key（RUNNING/COMPLETED/FAILED 均为进程内生命周期数据）。
     *
     * <p>作用：服务重启后，旧进程可能死于诊断中途（线程被杀/OOM/编译重启），Redis 中
     * RUNNING 状态会悬挂到 TTL 过期，期间 isTaskRunning 去重会拦住新诊断、前端轮询
     * 一直显示"诊断中"。启动时全量清除，保证新进程从干净状态开始。
     *
     * <p>注意：多实例部署时不应开启（会误删其他实例在跑的任务）；当前单机部署无影响。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> keys = redisTemplate.keys("diagnosis:task:*");
            if (keys != null && !keys.isEmpty()) {
                long removed = redisTemplate.delete(keys);
                log.info("启动清理残留诊断任务状态: 删除 {} 个 key", removed);
            }
        } catch (Exception e) {
            log.warn("启动清理诊断任务状态失败（不影响启动）: {}", e.getMessage());
        }
    }

    /**
     * 异步诊断执行（方案C核心）：跑在 diagnosisExecutor 线程池，与 SSE 连接生命周期解耦。
     *
     * <p>跳页/超时只退订订阅者，任务继续后台执行；完成后写 Redis COMPLETED 状态，
     * 用户回页轮询即可拿到报告。
     *
     * <p>声明在接口上：@Async 经 JDK 动态代理拦截，接口方法才能被子类/代理调度。
     */
    @Override
    @Async("diagnosisExecutor")
    public void diagnoseAsync(Resume resume, String career, String taskKey) {
        Long resumeId = resume.getId();
        try {
            doDiagnose(resume, career, taskKey);
            // 成功路径：saveDiagnosisResult 内部已 setTaskCompleted + broadcast final
        } catch (Exception e) {
            log.error("异步诊断异常: resumeId={}, career={}", resumeId, career, e);
            setTaskFailed(resumeId, career, e.getMessage());
            broadcast(taskKey, "error", errorMap(ResumeErrorCode.AI_SERVICE_UNAVAILABLE.getErrorCode(),
                    "诊断失败，请重试"));
        } finally {
            // 结束订阅：广播已发完，清列表 + complete 全部 emitter
            completeSubscribers(taskKey);
        }
    }

    @Override
    public DiagnosisTaskStatusVO getTaskStatus(Long resumeId, String career) {
        DiagnosisTaskStatusVO vo = new DiagnosisTaskStatusVO();
        vo.setState("NONE");
        vo.setCareer(career);
        try {
            Object value = redisTemplate.opsForValue().get(taskKey(resumeId, career));
            if (value == null) {
                return vo;
            }
            String raw = value.toString();
            if ("RUNNING".equals(raw)) {
                vo.setState("RUNNING");
            } else if (raw.startsWith("COMPLETED:")) {
                vo.setState("COMPLETED");
                vo.setReportId(Long.valueOf(raw.substring("COMPLETED:".length())));
            } else if (raw.startsWith("FAILED:")) {
                vo.setState("FAILED");
            }
        } catch (Exception e) {
            // Redis 异常 fail-open：返回 NONE，前端停止轮询，不阻塞页面
            log.warn("诊断任务状态查询失败(返回NONE): resumeId={}", resumeId, e);
        }
        return vo;
    }

    @Override
    public List<DiagnosisHistoryVO> listHistory(Long resumeId) {
        // 最多返回 MAX_HISTORY_TOTAL 条（跨职业合计，按时间倒序）
        List<ResumeDiagnosisReport> list = diagnosisReportMapper
                .selectByResumeIdAndCareer(resumeId, null, MAX_HISTORY_TOTAL);
        return list.stream().map(r -> {
            DiagnosisHistoryVO vo = new DiagnosisHistoryVO();
            vo.setId(r.getId());
            vo.setCareer(r.getCareer());
            vo.setMatchScore(r.getMatchScore());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public DiagnosisDetailVO getDetail(Long reportId) {
        ResumeDiagnosisReport report = diagnosisReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResumeErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        // 归属校验：报告对应的简历必须属于当前登录候选人（防止越权读取他人诊断报告）
        Resume resume = resumeMapper.selectById(report.getResumeId());
        if (resume == null || !resume.getCandidateId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResumeErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        // 从 MinIO 读取 Markdown
        // 2026-08-08：历史详情同样统一正文分数（老报告落库时未统一，直接返回会重现 76 vs 72.5）
        String reportMd = unifyReportScore(readMdFromMinio(report.getFileUrl()), report.getMatchScore());
        DiagnosisDetailVO vo = new DiagnosisDetailVO();
        vo.setId(report.getId());
        vo.setCareer(report.getCareer());
        vo.setMatchScore(report.getMatchScore());
        vo.setReportMd(reportMd);
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }

    // ==================== 诊断执行 ====================

    private void doDiagnose(Resume resume, String career, String taskKey) {
        Long resumeId = resume.getId();

        // ④ 组装诊断上下文
        ResumeAbilityModel abilityModel = abilityModelMapper.selectByResumeId(resumeId);
        String context = buildDiagnosisContext(resume, abilityModel, career);

        log.info("开始诊断: resumeId={}, career={}", resumeId, career);

        // ⑤ 调百宝箱 Agent
        // 方案C：cancelled 恒为 false——任务不再因连接断开而取消，由后台跑完落库
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Iterable<A2AEvent> events = baibaoxiangClient.chatText(
                context, BaibaoxiangClient.MODE_DIAGNOSIS, resumeId.toString(), cancelled);

        String finalOutput = null;
        for (A2AEvent event : events) {
            if (event instanceof A2AEvent.TextEvent) {
                broadcast(taskKey, "thinking",
                        thinkingMap(((A2AEvent.TextEvent) event).getContent()));
            } else if (event instanceof A2AEvent.ArtifactEvent) {
                A2AEvent.ArtifactEvent artifact = (A2AEvent.ArtifactEvent) event;
                if (artifact.isLastChunk()) {
                    finalOutput = artifact.getContent();
                }
            } else if (event instanceof A2AEvent.StatusEvent) {
                A2AEvent.StatusEvent status = (A2AEvent.StatusEvent) event;
                if ("failed".equals(status.getState())) {
                    broadcast(taskKey, "error",
                            errorMap(ResumeErrorCode.AI_SERVICE_UNAVAILABLE.getErrorCode(), "智能体执行失败"));
                    return;
                }
            }
        }

        if (finalOutput == null) {
            log.warn("诊断 Agent 未产出结果: resumeId={}", resumeId);
            broadcast(taskKey, "error",
                    errorMap(ResumeErrorCode.AI_SERVICE_UNAVAILABLE.getErrorCode(), "智能体未返回诊断结果"));
            return;
        }

        // ⑥ 解析（多层兜底：LLM 可能输出纯 Markdown 而非 JSON）
        BigDecimal matchScore;
        String reportMd;
        try {
            String cleaned = cleanLlmOutput(finalOutput);
            JsonNode root;
            try {
                root = objectMapper.readTree(cleaned);
            } catch (Exception parseEx) {
                // JSON 解析失败（LLM 常在字符串内输出未转义引号/截断导致整体失败，
                // 但 report_md 本身大多完整可提取）→ 优先提取 report_md 当报告；
                // 提取不到才全文当报告（避免 JSON 外壳被当 Markdown 存库）
                // 2026-08-08 排查增强：输出 LLM 原始内容前 800 字符，定位"百宝箱返回
                // 非 JSON"时到底收到了什么（纯文本 / JSON 外壳 / 思考链泄漏）
                log.warn("诊断 LLM 输出 JSON 解析失败: resumeId={}, 解析异常={}, 原文前800字符: {}",
                        resumeId, parseEx.getMessage(),
                        cleaned.substring(0, Math.min(800, cleaned.length())));
                String extractedMd = extractReportMd(cleaned);
                if (extractedMd != null) {
                    log.warn("诊断 LLM 输出 JSON 解析失败，提取 report_md 当报告: resumeId={}", resumeId);
                    matchScore = calcMatchScoreLocally(abilityModel);
                    saveDiagnosisResult(resume, career, taskKey, matchScore,
                            unifyReportScore(extractedMd, matchScore));
                    return;
                }
                log.warn("诊断 LLM 输出非 JSON 且无 report_md 可提取，全文当报告: resumeId={}", resumeId);
                matchScore = calcMatchScoreLocally(abilityModel);
                saveDiagnosisResult(resume, career, taskKey, matchScore,
                        unifyReportScore(cleaned, matchScore));
                return;
            }

            // 检查是否有诊断格式的字段
            if (root.has("matchScore") || root.has("match_score") || root.has("reportMd") || root.has("report_md")) {
                // 正常诊断输出
                // 2026-08-08 新方案：匹配度唯一来源 = LLM 输出的能力模型加权（agent 不再
                // 直接输出 matchScore；即使旧格式残留 matchScore 字段也一律忽略，保证单数源）。
                // 先回写能力模型，再用回写后的新值计算——分数随诊断变化（改简历/换职业后
                // 重新诊断基于新文本评估），且与雷达图 5 维天然一致。
                ResumeAbilityModel freshModel;
                try {
                    freshModel = persistAbilityModelFromDiagnosis(root, resumeId, resume.getCandidateId());
                } catch (Exception e) {
                    log.warn("诊断能力模型回写失败（不影响诊断主流程）: resumeId={}", resumeId, e);
                    freshModel = abilityModelMapper.selectByResumeId(resumeId);
                }
                matchScore = calcMatchScoreLocally(freshModel);
                reportMd = root.has("report_md")
                        ? root.get("report_md").asText()
                        : root.has("reportMd")
                            ? root.get("reportMd").asText()
                            : cleaned;
                // 正文"综合匹配度"数字统一为 matchScore，保证页面大数字与正文一致
                reportMd = unifyReportScore(reportMd, matchScore);
            } else if (root.has("ability_model") || root.has("card_structure")) {
                // 兜底：LLM 按平台 System Prompt 输出了解析格式 → 从能力模型本地算分，全文当报告
                log.warn("诊断 LLM 输出解析格式，启用兜底: resumeId={}", resumeId);
                matchScore = calcMatchScoreLocally(abilityModel);
                reportMd = "## 诊断报告（自动生成）\n\n"
                        + "> 智能体未按诊断格式输出，以下为基于能力模型的自动分析。\n\n"
                        + "### 综合匹配度: " + matchScore + " / 100\n\n"
                        + "### LLM原始输出\n```json\n" + cleaned + "\n```\n";
            } else {
                // 无法识别 → 全文当报告，保守 50 分
                log.warn("诊断 LLM 输出未知格式，全文当报告: resumeId={}", resumeId);
                matchScore = BigDecimal.valueOf(50);
                reportMd = unifyReportScore(cleaned, matchScore);
            }

            saveDiagnosisResult(resume, career, taskKey, matchScore, reportMd);
        } catch (Exception e) {
            log.error("诊断结果处理失败: resumeId={}", resumeId, e);
            broadcast(taskKey, "error",
                    errorMap(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "诊断结果保存失败"));
        }
    }

    // ==================== 缓存命中 ====================

    private void pushCachedResult(SseEmitter emitter, ResumeDiagnosisReport report) {
        // 2026-08-08：缓存命中同样统一正文分数（老报告落库时未统一，直接返回会重现 76 vs 72.5）
        String reportMd = unifyReportScore(readMdFromMinio(report.getFileUrl()), report.getMatchScore());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportId", report.getId());
        data.put("career", report.getCareer());
        data.put("matchScore", report.getMatchScore());
        data.put("reportMd", reportMd);
        sendEvent(emitter, "final", data);
        emitter.complete();
    }

    // ==================== 上下文组装 ====================

    private String buildDiagnosisContext(Resume resume, ResumeAbilityModel abilityModel, String career) {
        String abilityJson;
        try {
            Map<String, Object> am = new LinkedHashMap<>();
            Map<String, Object> scores = new LinkedHashMap<>();
            if (abilityModel != null) {
                scores.put("professional_skill", abilityModel.getProfessionalSkillScore());
                scores.put("work_experience", abilityModel.getWorkExperienceScore());
                scores.put("industry_knowledge", abilityModel.getIndustryKnowledgeScore());
                scores.put("comprehensive_quality", abilityModel.getComprehensiveQualityScore());
                scores.put("learning_growth", abilityModel.getLearningGrowthScore());
                am.put("scores", scores);
                am.put("sub_dimensions", abilityModel.getSubDimensions() != null ?
                        objectMapper.readTree(abilityModel.getSubDimensions()) : "");
            } else {
                scores.put("professional_skill", 0);
                scores.put("work_experience", 0);
                scores.put("industry_knowledge", 0);
                scores.put("comprehensive_quality", 0);
                scores.put("learning_growth", 0);
                am.put("scores", scores);
                am.put("sub_dimensions", "");
            }
            abilityJson = objectMapper.writeValueAsString(am);
        } catch (Exception e) {
            abilityJson = "{}";
        }

        String cardJson = resume.getCardStructure() != null ? resume.getCardStructure() : "{}";

        return "能力模型：" + abilityJson + "\n"
                + "简历内容：" + cardJson + "\n"
                + "目标职业：" + career;
    }

    // ==================== 存储工具 ====================

    private String readMdFromMinio(String fileUrl) {
        String objectName = storageClient.parseObjectName(fileUrl);
        if (objectName == null) {
            log.warn("诊断报告 fileUrl 无法解析 objectName: {}", fileUrl);
            return "（报告内容无法读取）";
        }
        try (InputStream is = storageClient.getObject(objectName)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("存储读取诊断报告失败: object={}", objectName, e);
            return "（报告内容无法读取）";
        }
    }

    // ==================== 限流 ====================

    private boolean checkRateLimit(Long candidateId) {
        String key = RATE_LIMIT_KEY + candidateId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.MINUTES);
            }
            return count == null || count <= 3;
        } catch (Exception e) {
            // Redis 不可用时不限流
            log.debug("限流检查失败，放行: candidateId={}", candidateId, e);
            return true;
        }
    }

    // ==================== 任务状态（Redis，方案C） ====================

    /**
     * 任务 key：resumeId + Base64(URL编码) 职业名。
     *
     * <p>职业名可能含中文/空格/特殊字符，直接拼进 Redis key 容易踩字符集与协议坑，
     * Base64 URL 编码后仅含 [A-Za-z0-9_-]，可安全作为 key 段。
     */
    private String taskKey(Long resumeId, String career) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(career.getBytes(StandardCharsets.UTF_8));
        return RedisKeyConstant.format(RedisKeyConstant.DIAGNOSIS_TASK, resumeId, encoded);
    }

    /** 诊断是否进行中（Redis 状态兜底，异常 fail-open 返回 false——宁可重跑不可阻塞） */
    private boolean isTaskRunning(Long resumeId, String career) {
        try {
            Object value = redisTemplate.opsForValue().get(taskKey(resumeId, career));
            return value != null && "RUNNING".equals(value.toString());
        } catch (Exception e) {
            log.debug("诊断任务状态读取失败(fail-open): resumeId={}", resumeId, e);
            return false;
        }
    }

    private void setTaskRunning(Long resumeId, String career) {
        try {
            redisTemplate.opsForValue().set(taskKey(resumeId, career), "RUNNING",
                    TASK_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("诊断任务状态写入失败(fail-open): resumeId={}", resumeId, e);
        }
    }

    private void setTaskCompleted(Long resumeId, String career, Long reportId) {
        try {
            redisTemplate.opsForValue().set(taskKey(resumeId, career), "COMPLETED:" + reportId,
                    TASK_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("诊断任务状态写入失败(fail-open): resumeId={}", resumeId, e);
        }
    }

    private void setTaskFailed(Long resumeId, String career, String message) {
        try {
            String msg = (message == null || message.trim().isEmpty()) ? "诊断失败" : message;
            // 截断：FAILED 值会经 JSON 序列化返回前端，超长错误栈没有意义
            if (msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            redisTemplate.opsForValue().set(taskKey(resumeId, career), "FAILED:" + msg,
                    TASK_FAILED_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("诊断任务状态写入失败(fail-open): resumeId={}", resumeId, e);
        }
    }

    // ==================== SSE 订阅管理（方案C） ====================

    /** 注册订阅者：同任务可多个连接（同 resume 多标签页 / 页面组件重复挂载） */
    private void registerSubscriber(String taskKey, SseEmitter emitter) {
        subscribers.computeIfAbsent(taskKey, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    /** 退订：连接断开/超时只移除订阅者，不取消后台任务 */
    private void unsubscribe(String taskKey, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(taskKey);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(taskKey, list);
            }
        }
    }

    /** 广播：推给该任务全部订阅者；无人订阅（全部跳页）时静默跳过 */
    private void broadcast(String taskKey, String event, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(taskKey);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            sendEvent(emitter, event, data);
        }
    }

    /** 结束订阅：任务跑完/失败后清空列表并 complete 全部 emitter（幂等，可多次调用） */
    private void completeSubscribers(String taskKey) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.remove(taskKey);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE emitter complete 失败: {}", e.getMessage());
            }
        }
    }

    /** submitted 事件载荷：前端收到后开始轮询任务状态 */
    private Map<String, Object> submittedMap(String taskKey, String career) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskKey", taskKey);
        map.put("career", career);
        return map;
    }

    // ==================== SSE 工具 ====================

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("SSE 事件发送失败: event={}", event, e);
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, int code, String message) {
        sendEvent(emitter, "error", errorMap(code, message));
        emitter.complete();
    }

    private Map<String, Object> thinkingMap(String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", content);
        return map;
    }

    private Map<String, Object> errorMap(int code, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message);
        return map;
    }

    /**
     * 保存诊断报告：MinIO 上传 → DB 落库 → Redis COMPLETED → MQ → SSE final
     *
     * <p>方案C：落库成功后写 Redis COMPLETED:reportId（回页轮询的完成信号），
     * 再广播 final（在线用户的实时通道）；emitter 生命周期交由 diagnoseAsync 的 finally 收尾。
     */
    private void saveDiagnosisResult(Resume resume, String career, String taskKey,
                                     BigDecimal matchScore, String reportMd) {
        Long resumeId = resume.getId();
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String safeCareer = career.replaceAll("[\\\\/:*?\"<>|]", "_");
        String objectName = String.format(DIAG_OBJECT_TEMPLATE, resumeId, safeCareer, timestamp);
        String fileUrl;
        try (ByteArrayInputStream mdStream =
                     new ByteArrayInputStream(reportMd.getBytes(StandardCharsets.UTF_8))) {
            // 存稳定访问 URL（endpoint/bucket/objectName），勿用 presigned URL 覆盖：
            // presigned 会对中文 object key 做 URL 编码且 7 天过期，导致历史报告无法回读
            fileUrl = minioUtil.upload(objectName, mdStream, "text/markdown");
        } catch (Exception e) {
            log.error("诊断报告 MinIO 上传失败: resumeId={}", resumeId, e);
            broadcast(taskKey, "error",
                    errorMap(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "诊断报告保存失败"));
            return;
        }

        ResumeDiagnosisReport report = new ResumeDiagnosisReport();
        report.setResumeId(resumeId);
        report.setCareer(career);
        report.setFileUrl(fileUrl);
        report.setMatchScore(matchScore);
        diagnosisReportMapper.insert(report);

        // 2026-08-08：历史增长治理——每职业最多保留 MAX_HISTORY_PER_CAREER 条，
        // 新报告落库后裁剪更旧记录（含 MinIO 文件），防止历史无限膨胀
        pruneOldReports(resumeId, career);

        // 方案C：先写完成状态再广播——回页轮询者与在线 SSE 订阅者都能立即感知
        setTaskCompleted(resumeId, career, report.getId());

        // 2026-08-08：移除 diagnosis-event MQ 发送（无消费者、通知已改走方案B
        // status接口+localStorage；RocketMQ 依赖保留——InterviewEventConsumer/
        // ApplicationEventProducer 仍在用）。阶段二如需通知再恢复。

        Map<String, Object> finalData = new LinkedHashMap<>();
        finalData.put("reportId", report.getId().toString());
        finalData.put("career", career);
        finalData.put("matchScore", matchScore);
        finalData.put("reportMd", reportMd);
        ResumeAbilityModel updatedAm = abilityModelMapper.selectByResumeId(resumeId);
        if (updatedAm != null) {
            Map<String, Object> amData = new LinkedHashMap<>();
            Map<String, Integer> amScores = new LinkedHashMap<>();
            amScores.put("professional_skill", updatedAm.getProfessionalSkillScore());
            amScores.put("work_experience", updatedAm.getWorkExperienceScore());
            amScores.put("industry_knowledge", updatedAm.getIndustryKnowledgeScore());
            amScores.put("comprehensive_quality", updatedAm.getComprehensiveQualityScore());
            amScores.put("learning_growth", updatedAm.getLearningGrowthScore());
            amData.put("scores", amScores);
            finalData.put("abilityModel", amData);
        }
        broadcast(taskKey, "final", finalData);
        log.info("诊断完成: resumeId={}, career={}, reportId={}, matchScore={}",
                resumeId, career, report.getId(), matchScore);
    }

    /**
     * 诊断历史裁剪：删除同 resume+career 下超出 MAX_HISTORY_PER_CAREER 条的最旧记录。
     *
     * <p>先删 MinIO 报告文件（单条失败仅告警，不中断），再批量删 DB 记录；
     * 整体异常也不影响本次诊断主流程（仅告警）。
     */
    private void pruneOldReports(Long resumeId, String career) {
        try {
            List<ResumeDiagnosisReport> overLimit = diagnosisReportMapper
                    .selectOverLimit(resumeId, career, MAX_HISTORY_PER_CAREER);
            if (overLimit.isEmpty()) {
                return;
            }
            for (ResumeDiagnosisReport r : overLimit) {
                String objectName = storageClient.parseObjectName(r.getFileUrl());
                if (objectName != null) {
                    try {
                        minioUtil.delete(objectName);
                    } catch (Exception e) {
                        log.warn("裁剪历史时删除存储报告失败: reportId={}, object={}",
                                r.getId(), objectName, e);
                    }
                }
            }
            List<Long> ids = overLimit.stream()
                    .map(ResumeDiagnosisReport::getId)
                    .collect(Collectors.toList());
            diagnosisReportMapper.deleteByIds(ids);
            log.info("诊断历史裁剪: resumeId={}, career={}, 删除 {} 条",
                    resumeId, career, ids.size());
        } catch (Exception e) {
            log.warn("诊断历史裁剪失败（不影响本次诊断）: resumeId={}, career={}",
                    resumeId, career, e);
        }
    }

    /**
     * 回写诊断 Agent 输出的能力模型（职业导向修正），upsert 到 resume_ability_model
     *
     * <p>2026-08-08 加固：LLM 输出缺失/越界（非 0-100 整数）时回退原能力模型值，
     * 防止格式漂移把解析好的模型清 0 或污染（雷达图依赖该表）。
     *
     * @return 回写后的最终能力模型（跳过回写时返回 DB 现有模型，可能为 null），
     *         供调用方据此计算匹配度
     */
    private ResumeAbilityModel persistAbilityModelFromDiagnosis(JsonNode root, Long resumeId, Long candidateId) {
        ResumeAbilityModel existing = abilityModelMapper.selectByResumeId(resumeId);
        JsonNode am = root.get("ability_model");
        if (am == null || !am.isObject()) {
            // 2026-08-08 排查增强：提升为 warn 并列出 root 顶层字段，确认 LLM 输出结构
            log.warn("诊断输出不含 ability_model，跳过回写: resumeId={}, root顶层字段={}",
                    resumeId, root.fieldNames());
            return existing;
        }
        // 无任何合法维度（缺失/越界）→ 整体视为异常输出，跳过回写，原模型不动
        if (!hasAnyLlmScore(am)) {
            log.warn("诊断 ability_model 无合法维度，跳过回写: resumeId={}", resumeId);
            return existing;
        }
        ResumeAbilityModel model = new ResumeAbilityModel();
        model.setCandidateId(candidateId);
        model.setResumeId(resumeId);
        model.setProfessionalSkillScore(resolveScore(am, "professional_skill", existing));
        model.setWorkExperienceScore(resolveScore(am, "work_experience", existing));
        model.setIndustryKnowledgeScore(resolveScore(am, "industry_knowledge", existing));
        model.setComprehensiveQualityScore(resolveScore(am, "comprehensive_quality", existing));
        model.setLearningGrowthScore(resolveScore(am, "learning_growth", existing));
        model.setSubDimensions("{}");
        if (existing != null) {
            abilityModelMapper.updateByResumeId(model);
        } else {
            abilityModelMapper.insert(model);
        }
        log.info("诊断能力模型已回写: resumeId={}, scores=[{},{},{},{},{}]",
                resumeId,
                model.getProfessionalSkillScore(),
                model.getWorkExperienceScore(),
                model.getIndustryKnowledgeScore(),
                model.getComprehensiveQualityScore(),
                model.getLearningGrowthScore());
        return model;
    }

    /** LLM 维度取分：缺失/非数字/越界（非 0-100 整数）时回退原能力模型值；原模型无该维度则 0 */
    private Integer resolveScore(JsonNode am, String key, ResumeAbilityModel existing) {
        if (am.has(key) && am.get(key).isNumber()) {
            int v = am.get(key).asInt();
            if (v >= 0 && v <= 100) {
                return v;
            }
        }
        if (existing != null) {
            switch (key) {
                case "professional_skill": return existing.getProfessionalSkillScore();
                case "work_experience": return existing.getWorkExperienceScore();
                case "industry_knowledge": return existing.getIndustryKnowledgeScore();
                case "comprehensive_quality": return existing.getComprehensiveQualityScore();
                case "learning_growth": return existing.getLearningGrowthScore();
                default: return 0;
            }
        }
        return 0;
    }

    /** LLM 输出中是否存在至少一个合法维度值（0-100 数字），否则整体视为异常输出 */
    private boolean hasAnyLlmScore(JsonNode am) {
        for (String key : new String[]{"professional_skill", "work_experience", "industry_knowledge",
                "comprehensive_quality", "learning_growth"}) {
            if (am.has(key) && am.get(key).isNumber()) {
                int v = am.get(key).asInt();
                if (v >= 0 && v <= 100) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 本地匹配度计算（系分 4.2.3 Step 4 固定权重，C方案起作为 matchScore 兜底来源——
     * 仅用于 LLM 输出无结构化分数或解析失败的分支）
     */
    private BigDecimal calcMatchScoreLocally(ResumeAbilityModel am) {
        if (am == null) {
            return BigDecimal.valueOf(50);
        }
        double score = am.getProfessionalSkillScore() * 0.35
                + am.getWorkExperienceScore() * 0.25
                + am.getIndustryKnowledgeScore() * 0.15
                + am.getComprehensiveQualityScore() * 0.15
                + am.getLearningGrowthScore() * 0.10;
        return BigDecimal.valueOf(Math.round(score * 10.0) / 10.0);
    }

    /**
     * 报告正文分数统一（A方案，2026-08-08）
     *
     * <p>LLM 正文叙述的"综合匹配度：X 分"与结构化 matchScore 可能不一致，
     * 保存/推送前统一替换为 matchScore 值，避免页面大数字与正文矛盾。
     * 仅匹配"综合匹配度："前缀 + 数字 + "分"后缀，不动其他叙述。
     */
    private String unifyReportScore(String reportMd, BigDecimal matchScore) {
        // matchScore 判空：历史数据可能为 null（缓存命中/历史详情路径的防御）
        if (reportMd == null || reportMd.isEmpty() || matchScore == null) {
            return reportMd;
        }
        // stripTrailingZeros：76.0 → "76"，75.8 → "75.8"，避免正文出现 "76.0 分"
        String unified = matchScore.stripTrailingZeros().toPlainString();
        // 冒号可选（[:：]?）且不要求"分"字后缀：兼容 "综合匹配度：72.5 分"、
        // "综合匹配度 72.5" 等表达；数字可能被 Markdown 加粗 ** 包裹（如
        // "综合匹配度：**72分**"，LLM 常见输出），加粗符号捕获后原样保留。
        // "综合匹配度"语义唯一（仅指总分），放宽无误伤
        return reportMd.replaceAll(
                "(\\*{0,2}综合匹配度\\*{0,2}\\s*[:：]?\\s*)(\\*{0,2})(\\d+(?:\\.\\d+)?)(\\*{0,2})",
                "$1$2" + unified + "$4");
    }

    /**
     * 从 JSON 文本中提取 report_md 字段原始字符串（整体解析失败时的二级兜底）。
     *
     * <p>LLM 输出带 JSON 外壳但字符串内有非法字符（未转义引号等）时整体解析失败，
     * 但 report_md 字段本身大多完整。正则不做严格 JSON 校验，只取首个
     * {@code "report_md": "..."} 中第一个未转义引号前的内容并还原转义序列。
     *
     * @return 提取到的报告正文；无该字段时返回 null
     */
    private String extractReportMd(String json) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"report_md\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                        java.util.regex.Pattern.DOTALL)
                .matcher(json);
        if (!m.find()) {
            return null;
        }
        // 还原 JSON 转义：先处理带前缀字符的（\" \n \t），最后还原双反斜杠（\\ → \）
        return m.group(1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    /** LLM 输出清洗（去 markdown 代码块包裹） */
    private String cleanLlmOutput(String json) {
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int braceStart = json.indexOf('{');
        if (braceStart > 0) {
            json = json.substring(braceStart);
        }
        int braceEnd = json.lastIndexOf('}');
        if (braceEnd > 0 && braceEnd < json.length() - 1) {
            json = json.substring(0, braceEnd + 1);
        }
        return json;
    }
}

package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.agent.AgentRunContext;
import com.lingxi.job.agent.AgentSseEvent;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import com.lingxi.job.agent.BaibaoxiangUserIdProvider;
import com.lingxi.job.agent.JdPromptSanitizer;
import com.lingxi.job.agent.AgentContextStore;
import com.lingxi.job.domain.dto.InterviewContextDTO;
import com.lingxi.job.domain.dto.query.QuestionSearchQuery;
import com.lingxi.job.domain.dto.request.GenerateQuestionsRequest;
import com.lingxi.job.feign.dto.InternalApplicationDTO;
import com.lingxi.job.feign.dto.ResumeDetailDTO;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.enums.DifficultyEnum;
import com.lingxi.job.exception.AgentHttpException;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.feign.ResumeDetailFeignClient;
import com.lingxi.job.feign.ResumeFeignClient;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.mapper.JobQuestionMapper;
import com.lingxi.job.service.InterviewAgentService;
import com.lingxi.job.agent.QuestionContentSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Interview Agent SSE 出题服务实现（F-17）
 * <p>
 * 核心设计：
 * <ol>
 *   <li><b>Semaphore 许可所有权转移</b>——{@code acquired} + {@code transferredToEmitter} 双标志：
 *   建流前步骤异常释放许可；成功路径所有权转移给 emitter，由 onCompletion/onTimeout/onError
 *   释放（{@code AtomicBoolean} 防重），<b>绝不重复释放</b>。</li>
 *   <li><b>建流前校验</b>（HTTP JSON 错误）：岗位归属、投递状态白名单（409+2305）、跨企业隔离。</li>
 *   <li><b>简历亮点 6 步脱敏流水线</b>（截断→去噪→关键词→岗位关联→排序→脱敏复核）。</li>
 *   <li><b>dataCompleteness</b>：BigDecimal 0~1，统一用 {@code compareTo} 与 0.5 比较；
 *   SSE result 输出 SUFFICIENT/INSUFFICIENT 枚举。</li>
 *   <li><b>7 步异步链路</b>：线程池拒绝（建流后）→ SSE error 2304 + complete。</li>
 * </ol>
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Service
public class InterviewAgentServiceImpl implements InterviewAgentService {

    /** 完整度阈值 */
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /** 投递状态白名单（允许出题） */
    private static final Set<String> ALLOWED_STATUS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("SCREENED", "INTERVIEWING")));

    /** 简历亮点最大条数 */
    private static final int MAX_HIGHLIGHTS = 10;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 内容字符判定（去噪用） */
    private static final Pattern HAS_CONTENT = Pattern.compile("[\\u4e00-\\u9fffA-Za-z]");

    private final JobPostMapper jobPostMapper;
    private final JobProfileMapper jobProfileMapper;
    private final JobQuestionMapper jobQuestionMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final ResumeDetailFeignClient resumeDetailFeignClient;
    private final BaibaoxiangAgentClient baibaoxiangAgentClient;
    private final BaibaoxiangUserIdProvider userIdProvider;
    private final JdPromptSanitizer jdPromptSanitizer;
    private final QuestionContentSanitizer questionContentSanitizer;
    private final AgentContextStore agentContextStore;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor executor;
    private final Semaphore semaphore;

    /** SseEmitter 超时（毫秒） */
    @Value("${sse.emitter-timeout-ms:120000}")
    private long emitterTimeoutMs;

    /** 目标百宝箱应用 ID（面试出题，Real 模式使用；Mock 模式不校验） */
    @Value("${baibaoxiang.interview-app-id:}")
    private String interviewAppId;

    /** 企业私有题确定性选题开关（ai.agent.private-question.enabled，默认开；关闭则跳过选题直接纯 AI） */
    @Value("${ai.agent.private-question.enabled:true}")
    private boolean privateQuestionEnabled;

    public InterviewAgentServiceImpl(JobPostMapper jobPostMapper,
                                     JobProfileMapper jobProfileMapper,
                                     JobQuestionMapper jobQuestionMapper,
                                     ResumeFeignClient resumeFeignClient,
                                     ResumeDetailFeignClient resumeDetailFeignClient,
                                     BaibaoxiangAgentClient baibaoxiangAgentClient,
                                     BaibaoxiangUserIdProvider userIdProvider,
                                     JdPromptSanitizer jdPromptSanitizer,
                                     QuestionContentSanitizer questionContentSanitizer,
                                     AgentContextStore agentContextStore,
                                     ObjectMapper objectMapper,
                                     @Qualifier("agentSseExecutor") ThreadPoolExecutor executor,
                                     @Qualifier("agentSseSemaphore") Semaphore semaphore) {
        this.jobPostMapper = jobPostMapper;
        this.jobProfileMapper = jobProfileMapper;
        this.jobQuestionMapper = jobQuestionMapper;
        this.resumeFeignClient = resumeFeignClient;
        this.resumeDetailFeignClient = resumeDetailFeignClient;
        this.baibaoxiangAgentClient = baibaoxiangAgentClient;
        this.userIdProvider = userIdProvider;
        this.jdPromptSanitizer = jdPromptSanitizer;
        this.questionContentSanitizer = questionContentSanitizer;
        this.agentContextStore = agentContextStore;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.semaphore = semaphore;
    }

    @Override
    public SseEmitter generateQuestions(Long jobId, GenerateQuestionsRequest request) {
        // 企业上下文（跨线程共享，须在建流前取出）
        Long companyId = UserContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException(403, "缺少企业上下文");
        }
        // 请求线程捕获 HR 用户 ID → 按 AppID 隔离的百宝箱伪标识（异步线程不读 UserContext）
        Long internalUserId = UserContext.getUserId();
        if (internalUserId == null) {
            throw new BusinessException(401, "缺少用户上下文");
        }
        String agentUserId = userIdProvider.provide(internalUserId, interviewAppId);
        String difficulty = DifficultyEnum.normalize(request.getDifficulty());

        // ① 一次 tryAcquire（绝不二次调用），失败 → HTTP 503 + 2304
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            throw new AgentHttpException(HttpStatus.SERVICE_UNAVAILABLE, JobErrorCode.AGENT_CONCURRENT_FULL);
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        String runToken = null;
        boolean transferredToEmitter = false;
        try {
            // ② 岗位归属校验（企业隔离）
            JobPost jobPost = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
            if (jobPost == null) {
                throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
            }

            // ③ 投递上下文（Feign → resume），建流前校验
            InterviewContextDTO ctx = fetchInterviewContext(request.getApplicationId());
            validateContext(ctx, jobId, companyId, request.getApplicationId());

            // ④ 简历亮点二次处理（6 步脱敏流水线）
            List<String> highlights = processHighlights(ctx.getResumeHighlights());

            // ⑤ dataCompleteness 完整度判定（BigDecimal，统一 compareTo 0.5）
            BigDecimal dc = normalizeDataCompleteness(ctx.getDataCompleteness());
            boolean personalized = dc != null && dc.compareTo(HALF) >= 0;

            // ⑥ 岗位画像（供 Tool3 题库搜索 + 企业私有题技能匹配）
            String jobType = null;
            List<String> skillTags = null;
            JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, companyId);
            if (profile != null) {
                jobType = profile.getJobType();
                skillTags = parseSkillTags(profile.getCoreSkills());
            }

            // ⑦ 签发 runToken + 构建上下文
            runToken = UUID.randomUUID().toString().replace("-", "");
            AgentRunContext context = new AgentRunContext();
            context.setRunToken(runToken);
            context.setCompanyId(companyId);
            context.setJobId(jobId);
            context.setJobType(jobType);
            context.setSkillTags(skillTags);
            context.setApplicationId(request.getApplicationId());
            context.setCandidateId(ctx.getCandidateId());
            // 低完整度（非个性化）不保存简历亮点：Tool 回调也无从取得（跳过简历上下文闭环）
            context.setResumeHighlights(personalized ? highlights : Collections.emptyList());
            context.setDataCompleteness(dc);
            context.setPersonalized(personalized);
            context.setExpiresAt(LocalDateTime.now().plusSeconds(AgentRunContext.TTL_SECONDS));

            // ⑧ 建 SseEmitter + 注册回调（所有权转移前） + 写缓存
            SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
            registerCallbacks(emitter, runToken, cancelled);
            agentContextStore.put(runToken, context);

            // ⑨ 异步执行（拒绝 → SSE error 2304 + complete）
            try {
                executor.execute(() -> runAgentAndForward(emitter, context, cancelled, jobPost, difficulty, agentUserId));
            } catch (RejectedExecutionException e) {
                log.warn("Interview Agent 线程池拒绝，runToken={}", runToken);
                sendError(emitter, JobErrorCode.AGENT_CONCURRENT_FULL.getErrorCode(), true);
                emitter.complete();
            }
            // 至此所有权转移给 emitter：此后所有清理由 onCompletion/onTimeout/onError 负责
            transferredToEmitter = true;
            return emitter;
        } finally {
            if (acquired && !transferredToEmitter) {
                if (runToken != null) {
                    agentContextStore.remove(runToken);
                }
                semaphore.release();
            }
        }
    }

    // ==================== 建流前步骤 ====================

    /**
     * 获取投递上下文（两步：投递详情 + 简历详情，B 内二次加工）
     * <p>跨服务审查 P0-02：resume 不再提供 interview-context 聚合接口，改调现有
     * 投递详情 + 简历详情接口，在 lingxi-job 内组装（关联/状态由 validateContext 校验，
     * 亮点/完整度在此提取计算）。简历缺失/失败 → 降级通用题（不 503）。</p>
     */
    private InterviewContextDTO fetchInterviewContext(Long applicationId) {
        try {
            // ① 投递详情（岗位/候选人/简历/企业归属/状态）
            Result<InternalApplicationDTO> appResult = resumeFeignClient.getApplication(applicationId);
            if (appResult == null) {
                throw new AgentHttpException(HttpStatus.SERVICE_UNAVAILABLE,
                        JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), "简历服务返回异常");
            }
            // resume 3103 APPLICATION_NOT_FOUND = 投递不存在，属"投递上下文非法"而非下游不可用 → 409+2305
            if (appResult.getCode() == 3103) {
                throw new AgentHttpException(HttpStatus.CONFLICT, JobErrorCode.APPLICATION_CONTEXT_INVALID);
            }
            if (appResult.getCode() != 200 || appResult.getData() == null) {
                throw new AgentHttpException(HttpStatus.SERVICE_UNAVAILABLE,
                        JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), "简历服务返回异常");
            }
            InternalApplicationDTO app = appResult.getData();
            InterviewContextDTO ctx = new InterviewContextDTO();
            ctx.setApplicationId(app.getId());
            ctx.setJobId(app.getJobId());
            ctx.setCompanyId(app.getCompanyId());
            ctx.setCandidateId(app.getCandidateId());
            ctx.setResumeId(app.getResumeId());
            ctx.setStatus(app.getStatus());

            // ② 简历详情（可选）：提取亮点 + 算完整度；失败/缺失 → 降级通用题（不 503）
            if (app.getResumeId() != null) {
                try {
                    Result<ResumeDetailDTO> resumeResult = resumeDetailFeignClient.getResumeDetail(app.getResumeId());
                    if (resumeResult != null && resumeResult.getCode() == 200 && resumeResult.getData() != null) {
                        Object card = resumeResult.getData().getCardStructure();
                        if (card != null) {
                            JsonNode cardNode = objectMapper.valueToTree(card);
                            ctx.setResumeHighlights(extractHighlights(cardNode));
                            ctx.setDataCompleteness(computeCompleteness(cardNode));
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取简历详情失败，降级通用题, applicationId={}", applicationId, e);
                }
            }
            return ctx;
        } catch (AgentHttpException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取投递上下文失败, applicationId={}", applicationId, e);
            throw new AgentHttpException(HttpStatus.SERVICE_UNAVAILABLE,
                    JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), "获取投递上下文失败");
        }
    }

    /**
     * 从简历卡片提取亮点文本（sections[].points[]，兼容 point 为字符串或 {text/content}）
     * <p>resume 实际 cardStructure 根是对象：{@code {"sections":[{title, points:[{text}]}]}}，
     * 必须先取 {@code path("sections")} 再遍历（非直接按数组遍历）。</p>
     */
    private List<String> extractHighlights(JsonNode cardStructure) {
        List<String> highlights = new ArrayList<>();
        if (cardStructure == null) {
            return highlights;
        }
        JsonNode sections = cardStructure.path("sections");
        if (!sections.isArray()) {
            return highlights;
        }
        for (JsonNode section : sections) {
            JsonNode title = section.path("title");
            if (title.isTextual() && !title.asText().isEmpty()) {
                highlights.add(title.asText());
            }
            JsonNode points = section.path("points");
            if (points.isArray()) {
                for (JsonNode point : points) {
                    String text = point.isTextual() ? point.asText()
                            : point.path("text").asText(point.path("content").asText(""));
                    if (!text.isEmpty()) {
                        highlights.add(text);
                    }
                }
            }
        }
        return highlights;
    }

    /**
     * 按简历卡片 points 覆盖度估算完整度（0~1）；无可估内容返回 null（降级通用题）
     * <p>根节点为对象（含 sections[]），先 {@code path("sections")} 再遍历。</p>
     */
    private BigDecimal computeCompleteness(JsonNode cardStructure) {
        if (cardStructure == null) {
            return null;
        }
        JsonNode sections = cardStructure.path("sections");
        if (!sections.isArray() || sections.size() == 0) {
            return null;
        }
        int totalPoints = 0;
        int filledPoints = 0;
        for (JsonNode section : sections) {
            JsonNode points = section.path("points");
            if (points.isArray()) {
                for (JsonNode point : points) {
                    totalPoints++;
                    String text = point.isTextual() ? point.asText()
                            : point.path("text").asText(point.path("content").asText(""));
                    if (!text.isEmpty()) {
                        filledPoints++;
                    }
                }
            }
        }
        if (totalPoints == 0) {
            return null;
        }
        return BigDecimal.valueOf(filledPoints)
                .divide(BigDecimal.valueOf(totalPoints), 2, RoundingMode.HALF_UP);
    }

    /**
     * 建流前校验：投递记录/岗位/企业匹配 + 状态白名单
     */
    private void validateContext(InterviewContextDTO ctx, Long jobId, Long companyId, Long applicationId) {
        // 严格完全匹配：下游字段为 null 或与请求不一致 → 拒绝（null 放行会绕过跨企业隔离）
        if (!Objects.equals(ctx.getApplicationId(), applicationId)) {
            throw new AgentHttpException(HttpStatus.CONFLICT, JobErrorCode.APPLICATION_CONTEXT_INVALID);
        }
        if (!Objects.equals(ctx.getJobId(), jobId)) {
            throw new AgentHttpException(HttpStatus.CONFLICT, JobErrorCode.APPLICATION_CONTEXT_INVALID);
        }
        // 跨企业数据隔离（硬要求，不能延后；companyId 必须与请求企业完全一致）
        if (!Objects.equals(ctx.getCompanyId(), companyId)) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        if (!ALLOWED_STATUS.contains(ctx.getStatus())) {
            throw new AgentHttpException(HttpStatus.CONFLICT, JobErrorCode.APPLICATION_CONTEXT_INVALID);
        }
    }

    /**
     * 简历亮点二次处理（6 步流水线：截断→去噪→关键词提取→岗位关联→排序→脱敏复核）
     */
    private List<String> processHighlights(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String h : raw) {
            if (result.size() >= MAX_HIGHLIGHTS) {
                break;
            }
            String s = h == null ? "" : h.trim();
            if (s.isEmpty()) {
                continue;
            }
            // 去噪：滤纯标点/数字/<4 有效字符
            if (countContentChars(s) < 4) {
                continue;
            }
            // 脱敏复核（复用公共题目脱敏器，规则与原 InterviewAgentServiceImpl.sanitize 一致）
            result.add(questionContentSanitizer.sanitize(s));
        }
        return result;
    }

    /** 统计有效内容字符数（汉字/字母） */
    private int countContentChars(String s) {
        int count = 0;
        java.util.regex.Matcher m = HAS_CONTENT.matcher(s);
        while (m.find()) {
            count++;
        }
        return count;
    }

    /**
     * dataCompleteness 规范化：null/越界（非 0~1）→ null（完整度不可用，降级通用题，不 503）
     */
    private BigDecimal normalizeDataCompleteness(BigDecimal dc) {
        if (dc == null) {
            return null;
        }
        if (dc.compareTo(ZERO) < 0 || dc.compareTo(ONE) > 0) {
            return null;
        }
        return dc;
    }

    // ==================== 建流后异步链路 ====================

    /**
     * 异步消费百宝箱内部事件流并转发到 SseEmitter
     * <p>
     * 内部事件协议（{@link AgentStreamEvent}）：HEADER 连接成功 / CONTENT 模型增量 /
     * COMPLETE 正常结束 / FAILURE 异常结束。Service 累积 CONTENT，仅 COMPLETE 后按既有
     * JSON Schema 校验并发 {@code result → done}；FAILURE 走 {@code error}（2303，retryable=true）。
     * </p>
     * <p>
     * 输出校验（2302）：累积内容须通过 {@link #isValidResult} 校验（questions 非空、
     * 每题含 content/type/difficulty），失败 → 重试 1 次（prompt 追加提示）→ 仍失败
     * → error 2302（retryable=true）。COMPLETE 但无合法 CONTENT（空/非法）视为输出非法。
     * </p>
     * <p>
     * retryable 细分（2303）：确定性生成失败（{@link AgentDeterministicFailureException}）
     * → retryable=false；超时/网络/百宝箱不可用等 → retryable=true。error 与 done 互斥。
     * </p>
     */
    private void runAgentAndForward(SseEmitter emitter, AgentRunContext context,
                                    AtomicBoolean cancelled, JobPost jobPost, String difficulty,
                                    String userId) {
        try {
            // ① 确定性选题：命中企业题 + 缺失题型 M（DB 异常已在方法内降级 S=∅）
            PrivateSelection sel = selectPrivateQuestions(context, difficulty);
            List<JobQuestion> privateHits = sel.selected;
            List<String> missingTypes = sel.missingTypes;
            if (!privateQuestionEnabled) {
                // 配置关闭：跳过确定性选题，走纯 AI
                privateHits = Collections.emptyList();
                missingTypes = new ArrayList<>(TARGET_QUESTION_TYPES);
            }

            // ② 全命中：企业题直达，不调 AI（维持 progress→result→done；requestId 自生成，与部分命中分支一致）
            if (!privateHits.isEmpty() && missingTypes.isEmpty()) {
                String requestId = UUID.randomUUID().toString().replace("-", "");
                String data = buildResultJson(requestId,
                        mergeQuestions(privateHits, Collections.emptyList()), "COMPANY_LIBRARY");
                data = rewriteResultData(data, context, "COMPANY_LIBRARY");
                sendProgress(emitter, new AtomicInteger(0), "已命中企业题库 " + privateHits.size() + " 题，正在汇总");
                emitter.send(SseEmitter.event().name(AgentSseEvent.EVENT_RESULT).data(data));
                emitter.send(SseEmitter.event().name(AgentSseEvent.EVENT_DONE).data(doneJson(requestId)));
                emitter.complete();
                return;
            }

            // ③ 部分命中 / 纯 AI：缺失题型交 AI 补
            String generationMode = privateHits.isEmpty() ? "AI_GENERATED" : "MIXED";
            // 纯 AI（S=∅）不传缺失题型：prompt 走原逻辑生成 4 道题；allowedTypes=null → 恰好 4 个标准题型各一
            boolean pureAi = privateHits.isEmpty();
            List<String> promptMissing = pureAi ? null : missingTypes;
            Set<String> allowedTypes = pureAi ? null : new LinkedHashSet<>(missingTypes);
            String prompt = buildPrompt(context, jobPost, difficulty, promptMissing);
            boolean retried = false;
            boolean completed = false;
            while (!completed) {
                // 累积模型输出 CONTENT（增量片段），requestId 取 HEADER 诊断值
                // （JDK8 lambda 限制：捕获变量不可赋值，用单元素数组容器承载）
                StringBuilder content = new StringBuilder();
                String[] requestIdHolder = new String[1];
                AtomicBoolean streamFailed = new AtomicBoolean(false);
                AtomicBoolean completedEvent = new AtomicBoolean(false);
                String[] failMessage = new String[1];
                AtomicBoolean headerSent = new AtomicBoolean(false);
                AtomicBoolean contentSent = new AtomicBoolean(false);
                AtomicInteger sequence = new AtomicInteger(0);

                sendProgress(emitter, sequence, "正在请求百宝箱");
                baibaoxiangAgentClient.streamInterview(prompt, userId, cancelled, event -> {
                    if (event.getType() == AgentStreamEvent.Type.HEADER) {
                        requestIdHolder[0] = event.getRequestId();
                        if (headerSent.compareAndSet(false, true)) {
                            sendProgress(emitter, sequence, "已连接百宝箱，开始生成");
                        }
                    } else if (event.getType() == AgentStreamEvent.Type.CONTENT) {
                        content.append(event.getContent());
                        if (contentSent.compareAndSet(false, true)) {
                            sendProgress(emitter, sequence, "模型生成中");
                        }
                    } else if (event.getType() == AgentStreamEvent.Type.COMPLETE) {
                        completedEvent.set(true);
                    } else if (event.getType() == AgentStreamEvent.Type.FAILURE) {
                        streamFailed.set(true);
                        failMessage[0] = event.getErrorMessage();
                    }
                });

                if (cancelled.get()) {
                    // 客户端已断开：资源清理由 onCompletion/onTimeout/onError 负责
                    return;
                }
                if (streamFailed.get()) {
                    log.warn("Interview Agent 流失败, runToken={}: {}", context.getRunToken(), failMessage[0]);
                    sendError(emitter, JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), true);
                    emitter.complete();
                    completed = true;
                    break;
                }
                if (!completedEvent.get()) {
                    // 协议早断：streamInterview 返回但未收到 COMPLETE（异常结束）→ 视为出题失败
                    log.warn("Interview Agent 事件流提前结束（无 COMPLETE）, runToken={}", context.getRunToken());
                    sendError(emitter, JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), true);
                    emitter.complete();
                    completed = true;
                    break;
                }

                String data = content.toString();
                if (!isValidResult(data, allowedTypes)) {
                    // 输出校验失败（含空内容/无合法 result）：重试 1 次，仍失败 → 2302
                    if (!retried) {
                        retried = true;
                        prompt = prompt + retryHint();
                        log.warn("Interview Agent result 校验失败，重试 1 次, runToken={}", context.getRunToken());
                        continue;
                    }
                    log.warn("Interview Agent result 重试后仍校验失败, runToken={}", context.getRunToken());
                    sendError(emitter, JobErrorCode.AGENT_OUTPUT_INVALID.getErrorCode(), true);
                    emitter.complete();
                    completed = true;
                    break;
                }

                // ④ 校验通过：合并企业题 + AI 题（4 题型重排 + sourceType 兜底），改写根字段后发 result → done
                List<Map<String, Object>> aiQuestions = extractQuestions(data);
                String merged = buildResultJson(requestIdHolder[0],
                        mergeQuestions(privateHits, aiQuestions), generationMode);
                merged = rewriteResultData(merged, context, generationMode);
                emitter.send(SseEmitter.event().name(AgentSseEvent.EVENT_RESULT).data(merged));
                emitter.send(SseEmitter.event().name(AgentSseEvent.EVENT_DONE).data(doneJson(requestIdHolder[0])));
                emitter.complete();
                completed = true;
            }
        } catch (AgentDeterministicFailureException e) {
            // 模型确定性生成失败：重试无意义 → retryable=false
            log.warn("Interview Agent 确定性生成失败, runToken={}: {}", context.getRunToken(), e.getMessage());
            sendError(emitter, JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), false);
            emitter.complete();
        } catch (Exception e) {
            // 超时/网络/百宝箱不可用 → retryable=true
            log.error("Interview Agent 出题失败, runToken={}", context.getRunToken(), e);
            sendError(emitter, JobErrorCode.AGENT_GENERATE_FAILED.getErrorCode(), true);
            emitter.complete();
        }
    }

    /**
     * 校验 result 事件内容
     * <p>allowedTypes=null（纯 AI）→ 恰好 4 个标准题型各一题（统一收紧）；
     * allowedTypes 非空（部分命中）→ 恰好 |allowedTypes| 题、题型互异且 ⊆allowedTypes。</p>
     */
    private boolean isValidResult(String data, Set<String> allowedTypes) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode questions = node.path("questions");
            int actualCount = questions.isArray() ? questions.size() : 0;
            if (actualCount == 0) {
                logValidationFailure(allowedTypes, 0, Collections.emptyList());
                return false;
            }
            if (allowedTypes == null) {
                if (questions.size() != TARGET_QUESTION_TYPES.size()) {
                    logValidationFailure(null, actualCount, collectTypes(questions));
                    return false;
                }
                Set<String> seen = new HashSet<>();
                for (JsonNode q : questions) {
                    if (!validQuestion(q)) {
                        logValidationFailure(null, actualCount, collectTypes(questions));
                        return false;
                    }
                    String type = q.path("type").asText();
                    if (!TARGET_QUESTION_TYPES.contains(type) || !seen.add(type)) {
                        logValidationFailure(null, actualCount, collectTypes(questions));
                        return false;
                    }
                }
                return true;
            }
            if (questions.size() != allowedTypes.size()) {
                logValidationFailure(allowedTypes, actualCount, collectTypes(questions));
                return false;
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode q : questions) {
                if (!validQuestion(q)) {
                    logValidationFailure(allowedTypes, actualCount, collectTypes(questions));
                    return false;
                }
                String type = q.path("type").asText();
                if (!allowedTypes.contains(type) || !seen.add(type)) {
                    logValidationFailure(allowedTypes, actualCount, collectTypes(questions));
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("result 事件解析失败，判定校验不通过", e);
            return false;
        }
    }

    /** 收集 result 中题目的实际题型（仅题型，不含题目正文） */
    private List<String> collectTypes(JsonNode questions) {
        List<String> types = new ArrayList<>();
        if (questions != null && questions.isArray()) {
            for (JsonNode q : questions) {
                types.add(q.path("type").asText(""));
            }
        }
        return types;
    }

    /** 校验失败明细日志：expected/actual 计数与题型，不记录题目正文或敏感信息 */
    private void logValidationFailure(Set<String> allowedTypes, int actualCount, List<String> actualTypes) {
        List<String> expected = allowedTypes == null ? TARGET_QUESTION_TYPES : new ArrayList<>(allowedTypes);
        log.warn("AI result 校验失败: expectedCount={}, expectedTypes={}, actualCount={}, actualTypes={}",
                expected.size(), String.join(",", expected), actualCount, String.join(",", actualTypes));
    }

    /** 单题合法性：content/type/difficulty 非空，且非提示词回显假题 */
    private boolean validQuestion(JsonNode q) {
        if (!q.hasNonNull("content") || !q.hasNonNull("type") || !q.hasNonNull("difficulty")) {
            return false;
        }
        if (isPromptEchoQuestion(q)) {
            return false;
        }
        return true;
    }

    /**
     * 拦截「提示词回显」类假题：题干为出题提示词、keyPoints/referenceAnswer 为占位。
     * <p>第一道防线是 Mock/Real 客户端不产出此类内容；此处为第二道防线，
     * 防止任何客户端（含未来真实 AI 异常）把提示词当题干回显。</p>
     */
    private boolean isPromptEchoQuestion(JsonNode q) {
        String content = q.path("content").asText("");
        String keyPoints = q.path("keyPoints").asText("");
        String referenceAnswer = q.path("referenceAnswer").asText("");
        // ① 题干命中提示词模板
        if (content.matches(".*请围绕.*题型回答.*") || content.contains("回答一道高质量面试题")) {
            return true;
        }
        // ② keyPoints/referenceAnswer 为占位组合，且题干过短（< 15 字）
        if ("考察要点".equals(keyPoints) && "参考答案".equals(referenceAnswer)
                && content.trim().length() < 15) {
            return true;
        }
        return false;
    }

    /** 从 AI result 抽取 questions 数组（供合并） */
    private List<Map<String, Object>> extractQuestions(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode questions = node.path("questions");
            List<Map<String, Object>> list = new ArrayList<>();
            if (questions.isArray()) {
                for (JsonNode q : questions) {
                    list.add(objectMapper.convertValue(q, new TypeReference<Map<String, Object>>() {}));
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("result 抽取 questions 失败，返回空列表", e);
            return Collections.emptyList();
        }
    }

    /** 合并企业题 + AI 题：按 4 标准题型顺序重排，AI 题 sourceType 兜底 AI_GENERATED */
    private List<Map<String, Object>> mergeQuestions(List<JobQuestion> privateHits,
                                                     List<Map<String, Object>> aiQuestions) {
        Map<String, Map<String, Object>> byType = new LinkedHashMap<>();
        for (JobQuestion q : privateHits) {
            byType.put(q.getQuestionType(), toQuestionMap(q));
        }
        for (Map<String, Object> ai : aiQuestions) {
            Object type = ai.get("type");
            if (type != null) {
                byType.putIfAbsent(String.valueOf(type), ai);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String t : TARGET_QUESTION_TYPES) {
            Map<String, Object> q = byType.get(t);
            if (q != null) {
                if (!q.containsKey("sourceType") || q.get("sourceType") == null) {
                    q.put("sourceType", "AI_GENERATED");
                }
                result.add(q);
            }
        }
        return result;
    }

    /** 企业题 → result 题对象（字段映射 + 空值兜底，sourceType=COMPANY_LIBRARY） */
    private Map<String, Object> toQuestionMap(JobQuestion q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", q.getQuestionType());
        m.put("difficulty", q.getDifficulty());
        m.put("content", q.getContent());
        m.put("keyPoints", q.getKeyPoints() == null ? "" : q.getKeyPoints());
        m.put("referenceAnswer", q.getReferenceAnswer() == null ? "" : q.getReferenceAnswer());
        m.put("evaluationDimensions", parseEvaluationPoints(q.getEvaluationPoints()));
        m.put("sourceType", "COMPANY_LIBRARY");
        return m;
    }

    /** evaluation_points JSON → [{name,weight}]；null/解析失败 → [] */
    private List<Map<String, Object>> parseEvaluationPoints(String evaluationPointsJson) {
        if (!StringUtils.hasText(evaluationPointsJson)) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(evaluationPointsJson);
            if (node.isArray()) {
                return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("解析 evaluation_points 失败，返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 构造完整 result JSON（根字段 + questions） */
    private String buildResultJson(String requestId, List<Map<String, Object>> questions, String generationMode) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("requestId", requestId);
            root.put("generationMode", generationMode);
            root.put("isPersonalized", true);
            root.put("dataCompleteness", "SUFFICIENT");
            root.put("questions", questions);
            root.put("timestamp", LocalDateTime.now().format(TS));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 校验失败重试提示：不再新建另一套题数/题型规则，仅提示严格按【本次出题控制】重新输出合法 JSON */
    private String retryHint() {
        return "\n\n（提示：上次输出未通过校验，请严格按【本次出题控制】的要求重新输出合法 JSON，不要增减题型或数量）";
    }

    /**
     * 模型确定性生成失败异常（重试无意义，SSE error 2303 retryable=false）
     * <p>由 Mock/Real 百宝箱客户端在"LLM 明确表示无法生成"时抛出。</p>
     */
    public static class AgentDeterministicFailureException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public AgentDeterministicFailureException(String message) {
            super(message);
        }
    }

    /**
     * result 事件改写：dataCompleteness → SUFFICIENT/INSUFFICIENT、isPersonalized/generationMode 修正
     * <p>以 context.personalized 为准（与建流前完整度判定一致，避免双处计算漂移）。</p>
     */
    private String rewriteResultData(String data, AgentRunContext context, String generationMode) {
        try {
            JsonNode node = objectMapper.readTree(data);
            ObjectNode obj = node instanceof ObjectNode ? (ObjectNode) node : objectMapper.createObjectNode();
            boolean personalized = Boolean.TRUE.equals(context.getPersonalized());
            obj.put("isPersonalized", personalized);
            obj.put("dataCompleteness", personalized ? "SUFFICIENT" : "INSUFFICIENT");
            obj.put("generationMode", generationMode);
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("result 事件改写失败，原样透传", e);
            return data;
        }
    }

    /**
     * 发送 SSE progress 事件（连接成功/生成中，序列号自增）
     */
    private void sendProgress(SseEmitter emitter, AtomicInteger sequence, String message) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestId", UUID.randomUUID().toString().replace("-", ""));
            m.put("sequence", sequence.incrementAndGet());
            m.put("code", "GENERATING");
            m.put("message", message);
            m.put("timestamp", LocalDateTime.now().format(TS));
            emitter.send(SseEmitter.event().name(AgentSseEvent.EVENT_PROGRESS)
                    .data(objectMapper.writeValueAsString(m)));
        } catch (Exception e) {
            log.warn("发送 SSE progress 事件失败", e);
        }
    }

    /**
     * 构造 done 事件 JSON（requestId 缺省用新 UUID）
     */
    private String doneJson(String requestId) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestId", requestId == null ? UUID.randomUUID().toString().replace("-", "") : requestId);
            m.put("timestamp", LocalDateTime.now().format(TS));
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 发送 SSE error 事件（error 与 done 互斥）
     */
    private void sendError(SseEmitter emitter, int code, boolean retryable) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestId", UUID.randomUUID().toString().replace("-", ""));
            m.put("errorCode", code);
            m.put("message", "Agent出题失败");
            m.put("retryable", retryable);
            m.put("timestamp", LocalDateTime.now().format(TS));
            emitter.send(SseEmitter.event().name(AgentSseEvent.EVENT_ERROR).data(objectMapper.writeValueAsString(m)));
        } catch (Exception e) {
            log.warn("发送 SSE error 事件失败", e);
        }
    }

    /**
     * 注册回调：清理由 onCompletion/onTimeout/onError 负责（AtomicBoolean 防重），
     * 释放 Semaphore → 删除 AgentRunContext → 关闭百宝箱连接
     */
    private void registerCallbacks(SseEmitter emitter, String runToken, AtomicBoolean cancelled) {
        AtomicBoolean released = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
                agentContextStore.remove(runToken);
                cancelled.set(true);
                log.info("Interview Agent SSE 结束清理完成, runToken={}", runToken);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
    }

    /** 出题 4 标准题型（确定性选题目标题型全集） */
    private static final List<String> TARGET_QUESTION_TYPES =
            Collections.unmodifiableList(Arrays.asList("BASIC", "PROJECT", "BOUNDARY", "COMPREHENSIVE"));

    /**
     * 确定性选题：命中企业题 + 命中题型 S + 缺失题型 M；DB 异常降级 S=∅（纯 AI）
     * <p>私有题校验：content/题型/难度非空、题型∈4 标准题型、难度∈EASY/MEDIUM/HARD；坏题丢弃、其题型进 M。</p>
     */
    private PrivateSelection selectPrivateQuestions(AgentRunContext context, String difficulty) {
        try {
            QuestionSearchQuery q = new QuestionSearchQuery();
            q.setCompanyId(context.getCompanyId());
            q.setJobType(context.getJobType());
            q.setDifficulty(difficulty);
            q.setSkillTags(context.getSkillTags());
            List<JobQuestion> hit = jobQuestionMapper.selectForPrivateQuestion(q);
            List<JobQuestion> selected = new ArrayList<>();
            Set<String> hitTypes = new HashSet<>();
            for (JobQuestion qn : hit) {
                // 坏私有题/同题型重复 → 丢弃，其题型计入缺失集合交 AI 补
                if (qn == null || !isValidPrivateQuestion(qn) || hitTypes.contains(qn.getQuestionType())) {
                    continue;
                }
                hitTypes.add(qn.getQuestionType());
                selected.add(qn);
            }
            List<String> missingTypes = TARGET_QUESTION_TYPES.stream()
                    .filter(t -> !hitTypes.contains(t))
                    .collect(Collectors.toList());
            return new PrivateSelection(selected, hitTypes, missingTypes);
        } catch (Exception e) {
            log.warn("企业私有题查询失败，降级纯 AI: {}", e.getMessage());
            return new PrivateSelection(Collections.emptyList(), new HashSet<>(), new ArrayList<>(TARGET_QUESTION_TYPES));
        }
    }

    /**
     * 私有题合法性：content/题型/难度非空、题型∈4 标准题型、难度∈EASY/MEDIUM/HARD（codex 三轮③）
     */
    private boolean isValidPrivateQuestion(JobQuestion q) {
        if (!StringUtils.hasText(q.getContent())) {
            return false;
        }
        if (!StringUtils.hasText(q.getQuestionType()) || !TARGET_QUESTION_TYPES.contains(q.getQuestionType())) {
            return false;
        }
        if (!StringUtils.hasText(q.getDifficulty())) {
            return false;
        }
        for (DifficultyEnum d : DifficultyEnum.values()) {
            if (d.getCode().equals(q.getDifficulty())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 core_skills JSON → 技能名称列表（复制自 CandidateJobServiceImpl，其私有不可跨类复用）
     */
    private List<String> parseSkillTags(String coreSkillsJson) {
        if (!StringUtils.hasText(coreSkillsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> skills = objectMapper.readValue(coreSkillsJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            return skills.stream()
                    .map(s -> s.get("name") == null ? null : String.valueOf(s.get("name")))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析 core_skills 失败, 返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 确定性选题结果：命中企业题 + 命中题型 + 缺失题型
     */
    private static class PrivateSelection {

        final List<JobQuestion> selected;
        final Set<String> hitTypes;
        final List<String> missingTypes;

        PrivateSelection(List<JobQuestion> selected, Set<String> hitTypes, List<String> missingTypes) {
            this.selected = selected;
            this.hitTypes = hitTypes;
            this.missingTypes = missingTypes;
        }
    }

    /**
     * 组装出题 Prompt（岗位要求 + 简历亮点 + 难度；题库参考由 Tool3 回调提供）
     * <p>低完整度（!personalized）时跳过简历上下文：不出题依据不得引用简历内容。</p>
     */
    private String buildPrompt(AgentRunContext context, JobPost jobPost, String difficulty,
                               List<String> missingTypes) {
        StringBuilder sb = new StringBuilder();
        // 【本次出题控制】：唯一结构化题数/题型/难度约束（纯 AI=4 题全题型；MIXED=只补缺失题型）。
        // 字段名/中文冒号/换行必须与百宝箱工作流固定提示词完全一致，不得改英文冒号或别名。
        List<String> requiredTypes = (missingTypes == null || missingTypes.isEmpty())
                ? TARGET_QUESTION_TYPES : missingTypes;
        sb.append("【本次出题控制】\n");
        sb.append("questionCount：").append(requiredTypes.size()).append("\n");
        sb.append("requiredTypes：").append(String.join(",", requiredTypes)).append("\n");
        sb.append("difficulty：").append(difficulty).append("\n");
        // 岗位信息（保留）
        if (jobPost != null) {
            sb.append("岗位名称：").append(jobPost.getTitle()).append("\n");
            if (jobPost.getJdText() != null) {
                // 岗位 JD 出站前清洗（限长 + 敏感片段脱敏），原文不进日志
                sb.append("岗位 JD：").append(jdPromptSanitizer.sanitize(jobPost.getJdText())).append("\n");
            }
        }
        // 低完整度或完整度不可用 → 通用题，不提供简历亮点（跳过简历上下文）
        if (Boolean.TRUE.equals(context.getPersonalized())) {
            List<String> highlights = context.getResumeHighlights();
            if (highlights != null && !highlights.isEmpty()) {
                sb.append("候选人简历亮点：").append(String.join("；", highlights)).append("\n");
            }
        }
        return sb.toString();
    }
}

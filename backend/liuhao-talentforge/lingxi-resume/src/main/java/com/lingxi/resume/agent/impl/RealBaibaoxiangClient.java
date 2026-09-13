package com.lingxi.resume.agent.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.resume.agent.A2AEvent;
import com.lingxi.resume.config.ResumeStorageClient;
import com.lingxi.resume.agent.BaibaoxiangClient;
import com.lingxi.resume.agent.RuleParseFallback;
import com.lingxi.resume.agent.SectionDef;
import com.lingxi.resume.agent.TikaTextExtractor;
import com.lingxi.resume.exception.ResumeErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 真实百宝箱聊天客户端（对齐百宝箱 API v1）
 *
 * <p>百宝箱实际 API 不是 A2A/JSON-RPC 2.0，而是自有格式：
 * <pre>
 * POST https://api.tbox.cn/api/chat
 * Body: {"appId":"xxx", "query":"简历文本", "userId":"lingxi-resume-{contextId}", "stream":true}
 * Response: SSE（id:/event:/data: 三行格式），chunk 逐字返回
 * </pre>
 * <p>userId 按 contextId（简历/诊断 ID）唯一化：平台按 userId 关联/续接会话，
 * 固定 userId 会让不同简历续接到同一会话导致串数据（2026-08-12 修复）。
 *
 * <p>PDF 不支持直传百宝箱——先经 Tika 提取文本，再作为 query 发送。
 * 当 {@code resume.agent.mock=false} 时替代 {@link MockBaibaoxiangClient} 注入。
 *
 * @author 成员C
 * @since 2026-08-05
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "resume.agent.mock", havingValue = "false")
public class RealBaibaoxiangClient implements BaibaoxiangClient {

    private static final int SSE_READ_TIMEOUT_MS = 180_000; // 工作流3节点串联需更长时间

    /**
     * SSE 队列读取超时（秒）：与 SSE_READ_TIMEOUT_MS 对齐。
     * 百宝箱首次会话（带全文）响应可能超过 60s（冷启动/工作流串联），
     * 60s 内无事件即降级规则引擎，导致卡片质量骤降；拉长到 180s 留足窗口。
     */
    private static final int SSE_POLL_TIMEOUT_SECONDS = 180;

    /** 取回查询：首次请求被平台 ~90s 网关切断后，带会话ID短查询取回已生成结果 */
    private static final String RETRIEVAL_QUERY =
            "请把上一轮对那份简历生成的完整解析 JSON 结果原样完整输出，不要省略任何字段。";

    /**
     * 诊断会话 ID 存储前缀：解析与诊断共用 contextId（resumeId），若共用同一个 Map key，
     * 诊断响应捕获的会话 ID 会覆盖解析的会话 ID——之后解析被 90s 切断取回时会命中诊断会话
     * （拿不到解析结果）。诊断会话 ID 独立存到 "diag:"+contextId（2026-08-12，方案B）。
     */
    private static final String DIAG_CONVERSATION_PREFIX = "diag:";

    /**
     * 简历文本中"损坏标记"的处理说明（拼在简历文本前）。
     * � 已在 TikaTextExtractor 源头替换为空格（不传 � 给百宝箱），替换后数字/日期出现
     * 连续异常空格（如 "1730  303"）——告知 LLM 这是提取损坏特征：包含异常空格的
     * 字段/要点直接省略（不输出、不推断、不编造），用户在线修改补齐；
     * 不含异常空格的内容必须正常输出（2026-08-12，OCR 环境不可用下的降级策略）。
     */
    private static final String REPLACEMENT_CHAR_HINT =
            "【重要】PDF 提取可能造成数字/日期中的字符损坏，表现为连续多余空格" +
            "（如 \"1730  303\"、\"2023.09  至今\"）：包含这种异常空格的字段/要点" +
            "（电话、日期、公司名、数字等）直接在输出中省略——" +
            "不要输出、不要编造、不要推断其内容；" +
            "不含异常空格的内容必须正常输出。\n";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TikaTextExtractor tikaTextExtractor;
    private final ResumeStorageClient storageClient;
    /** 规则引擎（预分段进度用：等待百宝箱生成期间推送真实章节标题） */
    private final RuleParseFallback ruleParseFallback;
    /** SSE 流读取线程池（替代裸 new Thread()，受控复用线程） */
    private final ExecutorService sseReaderExecutor;

    @Value("${baibaoxiang.api.url}")
    private String apiUrl;

    @Value("${baibaoxiang.api.auth:}")
    private String auth;

    @Value("${baibaoxiang.app-id:}")
    private String appId;

    /**
     * 会话 ID（按 contextId 存储；header chunk 捕获，SCORE 模式复用 CARD 阶段上下文）。
     * 用 Map 而非单字段：并发解析/诊断时单字段会被互相覆盖，导致串会话上下文。
     * 不清理：key = 历史解析过的简历/诊断 ID，量级小内存可接受。
     */
    private final Map<String, String> lastConversationIds = new ConcurrentHashMap<>();

    /**
     * Tika 提取的简历原文（按 contextId=resumeId 存储；MD 兜底与规则引擎降级用）。
     * 用 Map 而非单字段：并发解析时单字段会被互相覆盖，导致降级/MD 兜底串简历。
     * 不清理：key = 历史解析过的简历 ID，每条几 KB~几十 KB，量级小内存可接受。
     */
    private final Map<String, String> lastResumeTexts = new ConcurrentHashMap<>();

    public RealBaibaoxiangClient(TikaTextExtractor tikaTextExtractor,
                                 ResumeStorageClient storageClient,
                                 RuleParseFallback ruleParseFallback,
                                 @Qualifier("sseReaderExecutor") ExecutorService sseReaderExecutor) {
        this.tikaTextExtractor = tikaTextExtractor;
        this.storageClient = storageClient;
        this.ruleParseFallback = ruleParseFallback;
        this.sseReaderExecutor = sseReaderExecutor;
    }

    @Override
    public String getLastResumeText(String contextId) {
        return lastResumeTexts.get(contextId);
    }

    @Override
    public Iterable<A2AEvent> chatA2A(String pdfFileUrl, String contextId, AtomicBoolean cancelled) {
        return chatA2A(pdfFileUrl, null, contextId, cancelled);
    }

    @Override
    public Iterable<A2AEvent> chatText(String text, String mode, String contextId, AtomicBoolean cancelled) {
        String query = buildQuery(mode, text, contextId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("query", query);
        // userId 按 contextId 唯一化：百宝箱平台按 userId 关联/续接会话，
        // 固定 userId 会导致不同简历的请求续接到同一会话（串数据根因，2026-08-12 修复）
        body.put("userId", "lingxi-resume-" + contextId);
        body.put("stream", true);
        // DIAGNOSIS 不设 conversationId（每次诊断独立上下文）；
        // 诊断会话 ID 独立存储（diag: 前缀），不覆盖解析的会话 ID
        String convId = lastConversationIds.get(DIAG_CONVERSATION_PREFIX + contextId);
        if (!MODE_DIAGNOSIS.equals(mode) && convId != null && !convId.isEmpty()) {
            body.put("conversationId", convId);
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return errorEvents("请求体序列化失败");
        }

        HttpRequest httpRequest = HttpRequest.post(apiUrl)
                .body(jsonBody)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(SSE_READ_TIMEOUT_MS);

        if (auth != null && !auth.isEmpty()) {
            httpRequest.header("Authorization", auth);
        }

        log.info("发起百宝箱文本请求: url={}, appId={}, mode={}, 文本长度={}",
                apiUrl, appId, mode, text.length());

        return doStream(httpRequest, contextId, cancelled, null,
                DIAG_CONVERSATION_PREFIX + contextId); // DIAGNOSIS 无预分段进度，会话 key 独立
    }

    @Override
    public Iterable<A2AEvent> chatA2A(String pdfFileUrl, String mode, String contextId, AtomicBoolean cancelled) {
        // ① 本地提取 PDF 文本（百宝箱不支持 PDF 直传）
        String resumeText;
        try {
            resumeText = extractPdfText(pdfFileUrl);
        } catch (Exception e) {
            log.error("Tika 文本提取失败: pdfUrl={}", pdfFileUrl, e);
            return errorEvents("简历文本提取失败: " + e.getMessage());
        }
        lastResumeTexts.put(contextId, resumeText);

        // ② 构造请求体（query = 模式指令 + 简历文本）
        String query = buildQuery(mode, resumeText, contextId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("query", query);
        // userId 按 contextId 唯一化：百宝箱平台按 userId 关联/续接会话，
        // 固定 userId 会导致不同简历的请求续接到同一会话（串数据根因，2026-08-12 修复）
        body.put("userId", "lingxi-resume-" + contextId);
        body.put("stream", true);
        // 仅 SCORE（两阶段）需要复用 CARD 阶段的会话上下文；单次全量模式每次全新会话，
        // 杜绝旧会话命中（2026-08-12：全量模式携带 conversationId 存在串数据风险）
        String convId = lastConversationIds.get(contextId);
        if (MODE_SCORE.equals(mode) && convId != null && !convId.isEmpty()) {
            body.put("conversationId", convId);
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return errorEvents("请求体序列化失败");
        }

        // ③ HTTP POST + SSE 流式读取
        HttpRequest httpRequest = HttpRequest.post(apiUrl)
                .body(jsonBody)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(SSE_READ_TIMEOUT_MS);

        if (auth != null && !auth.isEmpty()) {
            httpRequest.header("Authorization", auth);
        }

        log.info("发起百宝箱请求: url={}, appId={}, mode={}, query长度={}, 文本长度={}",
                apiUrl, appId, mode, query.length(), resumeText.length());

        return doStream(httpRequest, contextId, cancelled, resumeText, contextId);
    }

    /** 提取 SSE 流式读取的公共逻辑（避免 chatA2A/chatText 重复） */
    private Iterable<A2AEvent> doStream(HttpRequest httpRequest, String contextId,
                                        AtomicBoolean cancelled, String resumeText,
                                        String conversationKey) {
        BlockingQueue<A2AEvent> queue = new LinkedBlockingQueue<>(64);
        // 受控线程池执行（sseReaderExecutor），替代裸 new Thread()：诊断异步化后
        // 并发诊断增多，裸线程无上限可创建数十线程；线程池复用 + 限流（queue=50 + CallerRuns）
        sseReaderExecutor.submit(
                () -> readSseIntoQueue(httpRequest, queue, cancelled, contextId, resumeText,
                        conversationKey));

        // 返回流式迭代器：有事件立即产出（thinking/progress 实时到达），流结束以哨兵标记
        return () -> new Iterator<A2AEvent>() {
            private A2AEvent pending;
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (pending != null) {
                    return true;
                }
                if (finished || cancelled.get()) {
                    return false;
                }
                try {
                    pending = queue.poll(SSE_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (pending == null) {
                        log.warn("百宝箱 SSE 读取超时: contextId={}", contextId);
                        finished = true;
                        return false;
                    }
                    if (pending instanceof A2AEvent.StatusEvent
                            && "completed".equals(((A2AEvent.StatusEvent) pending).getState())) {
                        pending = null;
                        finished = true;
                        return false;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finished = true;
                    return false;
                }
            }

            @Override
            public A2AEvent next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("SSE 事件流结束");
                }
                A2AEvent event = pending;
                pending = null;
                return event;
            }
        };
    }

    /**
     * SSE 流式读取（读取线程内执行）：逐行解析百宝箱事件 → 实时入队；
     * chunk 累积时扫描已闭合 section → progress 事件；流结束后入队结束哨兵。
     *
     * <p>前置步骤：等待百宝箱生成期间（平台为缓冲生成一次性返回，可能耗时几十秒），
     * 先用 Tika 文本经规则引擎预分段，逐条推送真实章节标题 progress——
     * "先粗后精"：规则引擎给骨架，LLM 给精修内容（不是假进度）。
     */
    private void readSseIntoQueue(HttpRequest httpRequest, BlockingQueue<A2AEvent> queue,
                                  AtomicBoolean cancelled, String contextId, String resumeText,
                                  String conversationKey) {
        // ① 预分段进度（仅解析模式有简历文本，诊断模式为 null 跳过）
        if (resumeText != null) {
            try {
            List<SectionDef> preSections = ruleParseFallback.segmentToDefs(resumeText);
            if (!preSections.isEmpty()) {
                List<String> preTitles = preSections.stream()
                        .map(SectionDef::getTitle)
                        .collect(Collectors.toList());
                for (int i = 0; i < preTitles.size(); i++) {
                    if (cancelled.get()) {
                        return;
                    }
                    put(queue, new A2AEvent.ProgressEvent("card", i + 1,
                            new ArrayList<>(preTitles.subList(0, i + 1))));
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            } catch (Exception e) {
                // 预分段失败不阻塞主流程（无预分段进度，直接等百宝箱）
                log.debug("预分段进度推送失败，忽略: contextId={}, error={}", contextId, e.getMessage());
            }
        }

        StringBuilder chunkBuffer = new StringBuilder();
        // 章节级进度扫描状态：已完成章节标题（跨 chunk 累积，progress 事件用）
        List<String> doneTitles = new ArrayList<>();
        // 耗时拆分（给百宝箱侧定位慢因）：HTTP 发起时刻 + 首个 chunk 到达时刻
        long httpStartMs = System.currentTimeMillis();
        try {
            int firstResult = readSseStream(httpRequest, chunkBuffer, doneTitles, queue, cancelled,
                    contextId, conversationKey);

            // 首次请求 0 chunk 但已捕获会话 ID → 短查询取回已生成结果。
            // 背景：平台网关约 90s 切断长生成（本简历生成耗时 ~91s 必被断），
            // 但结果已缓存在会话中；带 conversationId 的短查询取回耗时 < 90s，可绕过切断。
            // 按 conversationKey 取本请求类型的会话（解析=contextId，诊断=diag:contextId）
            String convId = lastConversationIds.get(conversationKey);
            if (chunkBuffer.length() == 0 && firstResult == 0
                    && !cancelled.get() && convId != null && !convId.isEmpty()) {
                log.warn("百宝箱首请求未返回内容，尝试取回已生成结果: contextId={}, conversationId={}",
                        contextId, convId);
                readSseStream(buildRetrieveRequest(contextId, convId), chunkBuffer,
                        doneTitles, queue, cancelled, contextId, conversationKey);
            }

            // 所有 chunk 拼接完毕 → 产出 ArtifactEvent（LLM 返回的完整 JSON）
            if (chunkBuffer.length() > 0) {
                String fullJson = chunkBuffer.toString();
                long doneMs = System.currentTimeMillis();
                log.info("百宝箱产出完成: contextId={}, 长度={}, 总耗时={}ms",
                        contextId, fullJson.length(), doneMs - httpStartMs);
                put(queue, new A2AEvent.ArtifactEvent("artifact-1", fullJson, true));
            } else if (firstResult == -2) {
                // 已取消：静默结束（结束哨兵由 finally 负责）
            } else {
                log.warn("百宝箱未返回有效 chunk: contextId={}", contextId);
                // 非 200 已在 readSseStream 中输出失败文本，这里只补状态
                if (firstResult != -1) {
                    put(queue, new A2AEvent.TextEvent("智能体未返回有效内容"));
                }
                put(queue, new A2AEvent.StatusEvent("failed", true));
            }
        } catch (Exception e) {
            log.error("百宝箱 SSE 异常", e);
            put(queue, new A2AEvent.TextEvent("智能体通信异常: " + e.getMessage()));
            put(queue, new A2AEvent.StatusEvent("failed", true));
        } finally {
            // 结束哨兵：迭代器据此终止（completed 状态在 Agent 事件循环中会被忽略）
            put(queue, new A2AEvent.StatusEvent("completed", true));
            log.info("百宝箱读取线程结束: contextId={}", contextId);
        }
    }

    /**
     * 执行一次 SSE 请求并读取事件：
     * <ul>
     *   <li>1 = 有内容（chunkBuffer 非空）</li>
     *   <li>0 = 正常流结束但无内容</li>
     *   <li>-1 = HTTP 非 200（已输出失败文本）</li>
     *   <li>-2 = 外部取消</li>
     * </ul>
     * 解析 SSE（id:/event:/data: 三行格式），chunk 累积到 chunkBuffer。
     */
    private int readSseStream(HttpRequest httpRequest, StringBuilder chunkBuffer,
                              List<String> doneTitles, BlockingQueue<A2AEvent> queue,
                              AtomicBoolean cancelled, String contextId,
                              String conversationKey) throws Exception {
        try (HttpResponse response = httpRequest.execute()) {
            if (response.getStatus() != 200) {
                String respBody = response.body();
                log.error("百宝箱请求失败: status={}, body={}", response.getStatus(), respBody);
                put(queue, new A2AEvent.TextEvent("智能体调用失败 HTTP " + response.getStatus()));
                return -1;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
                String line;
                SseEvent sse = new SseEvent();
                while ((line = reader.readLine()) != null) {
                    if (cancelled.get()) {
                        log.info("百宝箱对话被取消");
                        return -2;
                    }
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("id:")) {
                        sse.id = line.substring(3).trim();
                    } else if (line.startsWith("event:")) {
                        sse.event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        handleSseData(sse, line.substring(5).trim(), chunkBuffer, queue, doneTitles,
                                contextId, conversationKey);
                    }
                }
            }
            return chunkBuffer.length() > 0 ? 1 : 0;
        }
    }

    /**
     * 构造"取回"请求：短查询 + 已捕获会话 ID，请求平台返回上一轮生成结果。
     * 首次请求因平台 ~90s 网关切断失败时，结果已缓存在会话中，短查询取回耗时 < 90s。
     */
    private HttpRequest buildRetrieveRequest(String contextId, String conversationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("query", RETRIEVAL_QUERY);
        // 与发起请求保持相同 userId，确保命中本简历的会话而非他人会话
        body.put("userId", "lingxi-resume-" + contextId);
        body.put("stream", true);
        body.put("conversationId", conversationId);
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("取回请求体序列化失败", e);
        }
        HttpRequest retrieveRequest = HttpRequest.post(apiUrl)
                .body(jsonBody)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(SSE_READ_TIMEOUT_MS);
        if (auth != null && !auth.isEmpty()) {
            retrieveRequest.header("Authorization", auth);
        }
        return retrieveRequest;
    }

    /** 阻塞入队（中断时仅恢复中断标记，不向上抛） */
    private void put(BlockingQueue<A2AEvent> queue, A2AEvent event) {
        try {
            queue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== query 构建 ====================

    /**
     * 按模式构造 query：模式指令 + 简历文本
     *
     * @param contextId 会话上下文 ID（SCORE 模式判断是否复用 CARD 阶段的会话，避免重发简历文本）
     */
    private String buildQuery(String mode, String resumeText, String contextId) {
        String instruction;
        if (MODE_CARD.equals(mode)) {
            // 阶段1：只出卡片，快速返回
            instruction = "请仅输出简历的章节结构 JSON（card_structure 字段），不要输出 personal、ability_model、resume_md。" +
                    "严格按如下格式输出纯 JSON：{\"card_structure\":{\"sections\":[{\"title\":\"章节标题\",\"points\":[{\"id\":\"p1\",\"text\":\"内容行\"}]}]}}\n" +
                    "以下是要分析的简历文本：\n";
        } else if (MODE_DIAGNOSIS.equals(mode)) {
            // 诊断：角色/格式/要求全在百宝箱工作流诊断节点 System Prompt 中，
            // Java 侧只传上下文数据，不重写指令；
            // 且不拼 REPLACEMENT_CHAR_HINT——诊断上下文（能力模型/卡片 JSON）无 PDF 提取
            // � 问题，该指令会让诊断 LLM 误省略含空格的字段（2026-08-12 修复）
            return resumeText;
        } else if (MODE_SCORE.equals(mode)) {
            // 阶段2：只出评分
            instruction = "请仅输出简历的 personal 和 ability_model 两个字段的 JSON，" +
                    "不要输出 card_structure、resume_md。格式：" +
                    "{\"personal\":{\"name\":\"\",\"phone\":\"\",\"email\":\"\",\"wechat\":\"\"}," +
                    "\"ability_model\":{\"scores\":{\"professional_skill\":0,\"work_experience\":0,\"industry_knowledge\":0,\"comprehensive_quality\":0,\"learning_growth\":0},\"sub_dimensions\":{\"skills\":[],\"work_years\":\"\",\"sections\":[]}}}";
            String cardConvId = lastConversationIds.get(contextId);
            if (cardConvId != null && !cardConvId.isEmpty()) {
                // 复用会话：LLM 已有上下文，不重发简历文本
                instruction += "\n请基于之前对话中的简历文本完成以上任务。";
                return instruction;
            } else {
                instruction += "\n以下是要分析的简历文本：\n";
            }
        } else {
            // 全量模式（默认）：一次返回全部
            // 2026-08-12 强化：原指令只有 {...} 占位符且无忠实性约束，LLM 自由发挥空间大，
            // 导致"张冠李戴 + 内容编造"——补忠实原文硬性要求 + 完整结构样例（对齐 CARD/SCORE）
            instruction = "请解析以下简历文本，输出严格的 JSON。硬性要求：" +
                    "1. 严格忠实于简历原文，禁止编造、推测或补充原文没有的信息（公司、项目、技能、荣誉等一律不得虚构）。" +
                    "2. 按原文语义归类章节（教育背景/工作经历/项目经历/技能特长/自我评价等），禁止张冠李戴。" +
                    "3. 数字、日期、电话等原样保留。" +
                    "4. 仅输出 JSON，不要解释文字，不要 markdown 代码块。\n" +
                    "输出格式：{\"personal\":{\"name\":\"\",\"phone\":\"\",\"email\":\"\",\"wechat\":\"\"}," +
                    "\"card_structure\":{\"sections\":[{\"title\":\"章节标题\",\"points\":[{\"id\":\"p1\",\"text\":\"内容行\"}]}]}," +
                    "\"ability_model\":{\"scores\":{\"professional_skill\":0,\"work_experience\":0,\"industry_knowledge\":0,\"comprehensive_quality\":0,\"learning_growth\":0},\"sub_dimensions\":{\"skills\":[],\"work_years\":\"\",\"sections\":[]}}," +
                    "\"resume_md\":\"...\"}\n" +
                    "以下是要分析的简历文本：\n";
        }
        // � 处理说明拼在简历文本前（SCORE 复用会话分支提前 return，不发文本不需要）
        return instruction + REPLACEMENT_CHAR_HINT + resumeText;
    }

    // ==================== SSE 事件处理 ====================

    private static class SseEvent {
        String id;
        String event;
    }

    @SuppressWarnings("unchecked")
    private void handleSseData(SseEvent sse, String dataJson, StringBuilder chunkBuffer,
                               BlockingQueue<A2AEvent> queue, List<String> doneTitles,
                               String contextId, String conversationKey) {
        if (dataJson.isEmpty()) {
            return;
        }

        try {
            if ("end".equals(sse.event) || dataJson.contains("\"type\":\"end\"")) {
                log.debug("收到百宝箱结束事件: id={}", sse.id);
                return;
            }

            Map<String, Object> data = objectMapper.readValue(dataJson, Map.class);
            String type = (String) data.get("type");

            // header → 捕获 conversationId（两阶段复用会话上下文，按 contextId 隔离）
            if ("header".equals(type)) {
                String payloadJson = (String) data.get("payload");
                if (payloadJson != null) {
                    Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
                    String convId = (String) payload.get("sessionId");
                    if (convId != null && !convId.isEmpty()) {
                        lastConversationIds.put(conversationKey, convId);
                        log.info("捕获百宝箱会话ID: contextId={}, conversationId={}", contextId, convId);
                    }
                }
            } else if ("thinking".equals(type)) {
                // 思维链 → TextEvent → SSE:thinking（实时入队推送）
                String payloadJson = (String) data.get("payload");
                if (payloadJson != null) {
                    Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
                    String text = (String) payload.get("text");
                    if (text != null && !text.isEmpty()) {
                        put(queue, new A2AEvent.TextEvent(text));
                    }
                }
            } else if ("chunk".equals(type)) {
                // chunk → 收 buffer
                String payloadJson = (String) data.get("payload");
                if (payloadJson != null) {
                    Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
                    String text = (String) payload.get("text");
                    if (text != null) {
                        chunkBuffer.append(text);
                        // 实时扫描新闭合的 section → progress 事件（章节级流式进度，实时入队）
                        List<String> newTitles = extractNewSectionTitles(chunkBuffer.toString(),
                                doneTitles.size());
                        if (!newTitles.isEmpty()) {
                            doneTitles.addAll(newTitles);
                            put(queue, new A2AEvent.ProgressEvent("card",
                                    doneTitles.size(), new ArrayList<>(doneTitles)));
                        }
                    }
                    // 同时捕获 conversationId（有些版本放在 chunk payload 里）
                    String convId = (String) payload.get("conversationId");
                    if (convId != null && !convId.isEmpty()
                            && lastConversationIds.get(conversationKey) == null) {
                        lastConversationIds.put(conversationKey, convId);
                        log.info("从 chunk 捕获百宝箱会话ID: contextId={}, conversationId={}", contextId, convId);
                    }
                }
            }
            // meta 类型忽略
        } catch (Exception e) {
            log.warn("百宝箱 SSE data 解析失败: {}", dataJson, e);
        }
    }

    // ==================== 章节级进度扫描 ====================

    /**
     * 从累积的 LLM 输出中提取"新闭合"的 section 标题（轻量括号配对，不依赖完整 JSON 解析）
     *
     * <p>LLM 输出按 chunk 流式到达，JSON 在任意位置截断都是合法的中间态；
     * 本方法只找 {@code "sections":[ ... ]} 中已完整闭合的 {@code {...}} 对象，
     * 提取其 title 字段。解析失败（未闭合/结构异常）→ 返回空，等下一个 chunk。
     *
     * @param json          累积的 LLM 输出（可能不完整）
     * @param alreadyScanned 已扫描完成的 section 数量（跳过已推送的）
     * @return 新发现的 section 标题列表（可能为空）
     */
    private List<String> extractNewSectionTitles(String json, int alreadyScanned) {
        List<String> titles = new ArrayList<>();
        int sectionsIdx = json.indexOf("\"sections\"");
        if (sectionsIdx < 0) {
            return titles;
        }
        int arrStart = json.indexOf('[', sectionsIdx);
        if (arrStart < 0) {
            return titles;
        }

        int pos = arrStart + 1;
        int scanned = 0;
        while (pos < json.length()) {
            // 跳过空白（含换行/制表符）与分隔逗号——LLM 输出可能是格式化 JSON
            while (pos < json.length()
                    && (json.charAt(pos) == ' ' || json.charAt(pos) == '\n'
                    || json.charAt(pos) == '\r' || json.charAt(pos) == '\t'
                    || json.charAt(pos) == ',')) {
                pos++;
            }
            if (pos >= json.length() || json.charAt(pos) != '{') {
                break;
            }
            int end = findSectionEnd(json, pos);
            if (end < 0) {
                break; // 该 section 未闭合，等下一个 chunk
            }
            if (scanned >= alreadyScanned) {
                String title = extractSectionTitle(json.substring(pos, end + 1));
                if (title != null) {
                    titles.add(title);
                }
            }
            scanned++;
            pos = end + 1;
        }
        return titles;
    }

    /** 括号配对找 section 对象的闭合位置（跳过字符串内的括号），未闭合返回 -1 */
    private int findSectionEnd(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) {
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /** 从已闭合的 section 对象中提取 title 字段值（失败返回 null，不阻塞） */
    private String extractSectionTitle(String sectionJson) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(sectionJson);
        return m.find() ? m.group(1) : null;
    }

    // ==================== PDF 文本提取 ====================

    private String extractPdfText(String pdfFileUrl) throws Exception {
        String objectName = extractObjectName(pdfFileUrl);
        try (InputStream is = storageClient.getObject(objectName)) {
            return tikaTextExtractor.extractFromStream(is);
        }
    }

    private String extractObjectName(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "PDF URL 为空");
        }
        String objectName = storageClient.parseObjectName(url);
        if (objectName == null) {
            throw new BusinessException(ResumeErrorCode.PARSE_FAILED.getErrorCode(), "无法解析存储 objectName: " + url);
        }
        return objectName;
    }

    // ==================== 错误处理 ====================

    private List<A2AEvent> errorEvents(String reason) {
        List<A2AEvent> events = new ArrayList<>();
        events.add(new A2AEvent.TextEvent("简历解析服务暂不可用: " + reason));
        events.add(new A2AEvent.StatusEvent("failed", true));
        return events;
    }
}

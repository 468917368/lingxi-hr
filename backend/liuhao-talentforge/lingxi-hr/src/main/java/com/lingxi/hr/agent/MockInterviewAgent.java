package com.lingxi.hr.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.hr.agent.config.BaibaoxiangProperties;
import com.lingxi.hr.agent.config.HrAgentProperties;
import com.lingxi.hr.agent.config.MockInterviewProperties;
import com.lingxi.hr.agent.dto.MockAnswerRequest;
import com.lingxi.hr.agent.dto.MockAnswerResultVO;
import com.lingxi.hr.agent.dto.MockGenerateRequest;
import com.lingxi.hr.agent.dto.MockQuestionVO;
import com.lingxi.hr.agent.dto.MockReportVO;
import com.lingxi.hr.agent.entity.MockAnswer;
import com.lingxi.hr.agent.entity.MockReport;
import com.lingxi.hr.agent.entity.MockSession;
import com.lingxi.hr.agent.mapper.MockAnswerMapper;
import com.lingxi.hr.agent.mapper.MockReportMapper;
import com.lingxi.hr.agent.mapper.MockSessionMapper;
import com.lingxi.hr.agent.tools.FetchCandidateResumeTool;
import com.lingxi.hr.agent.tools.FetchJobRequirementsTool;
import com.lingxi.hr.exception.HrErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mock Interview Agent（三大场景编排）
 * <ul>
 *   <li>出题：岗位考察要点 + 简历能力画像 → 百宝箱生成题目</li>
 *   <li>评分：题目 + 作答 → 三维评分 + 点评</li>
 *   <li>报告：题目/评分聚合 → 亮点/短板/提升方案</li>
 * </ul>
 * 降级：LLM 不可用 → 出题走内置模板，评分置 0，报告按分数阈值规则生成。
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewAgent {

    private final MockSessionMapper sessionMapper;
    private final MockAnswerMapper answerMapper;
    private final MockReportMapper reportMapper;
    private final LlmClient llmClient;
    private final BaibaoxiangProperties baibaoxiangProperties;
    private final HrAgentProperties hrAgentProperties;
    private final MockInterviewProperties mockInterviewProperties;
    private final FetchJobRequirementsTool jobTool;
    private final FetchCandidateResumeTool resumeTool;
    private final MockPromptBuilder promptBuilder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 会话状态 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    // ==================== 场景 A：出题 ====================

    public void generate(MockGenerateRequest req, Long candidateId, AgentEventListener listener) {
        // 0. 每日次数校验
        checkQuota(candidateId);

        // 1. 建会话
        String sessionId = genSessionId();
        MockSession session = new MockSession();
        session.setSessionId(sessionId);
        session.setCandidateId(candidateId);
        session.setJobId(req.getJobId());
        session.setJobTitle(req.getJobTitle());
        session.setTotalQuestions(normalizeCount(req.getQuestionCount()));
        session.setStatus(STATUS_IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        // 2. 岗位输入
        notify(listener, "progress", progress(1, "FETCH_JOB", "正在获取岗位考察重点..."));
        String jobInfo = jobTool.fetch(req.getJobId(), req.getJobTitle());

        // 3. 简历输入
        notify(listener, "progress", progress(2, "FETCH_RESUME", "正在读取简历能力画像..."));
        FetchCandidateResumeTool.ResumeInput resumeInput = resumeTool.fetch(req.getResumeId());
        String resumeInfo = resumeInput.getText();

        // 4. 出题（LLM 不可用走内置模板）
        notify(listener, "progress", progress(3, "GENERATING", "AI正在基于岗位与简历定制题目..."));
        int count = normalizeCount(req.getQuestionCount());
        List<MockQuestionVO> questions = generateQuestions(req, candidateId, jobInfo, resumeInfo, count);

        // 5. 题目快照写入 mock_answer
        for (int i = 0; i < questions.size(); i++) {
            MockQuestionVO q = questions.get(i);
            MockAnswer answer = new MockAnswer();
            answer.setSessionId(sessionId);
            answer.setQuestionNumber(i + 1);
            answer.setQuestionContent(q.getContent());
            answer.setQuestionDimension(q.getDimension());
            answer.setQuestionType(q.getQuestionType());
            answer.setIsSkipped(0);
            answer.setCreatedAt(LocalDateTime.now());
            answerMapper.insert(answer);
        }

        // 6. SSE result + done
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("questions", questions);
        data.put("resumeUsed", resumeInput.isUsed());
        notify(listener, "result", data);
        notify(listener, "done", doneData());
    }

    // ==================== 场景 B：单题评分 ====================

    public void answer(String sessionId, MockAnswerRequest req, Long candidateId, AgentEventListener listener) {
        MockSession session = validateOwned(sessionId, candidateId);
        validateInProgress(session);

        if (req.getQuestionNumber() == null || req.getQuestionNumber() < 1) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_NOT_FOUND);
        }
        MockAnswer existing = answerMapper.selectBySessionIdAndQuestionNumber(sessionId, req.getQuestionNumber());
        if (existing == null) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_NOT_FOUND.getErrorCode(),
                    "题目不存在，请先重新生成面试");
        }

        // 评分（LLM 超时/失败 → 本题未评分）
        long timeout = hrAgentProperties.getLlm().getScoreTimeoutMs();
        String appId = baibaoxiangProperties.getAgent().getScoreAppId();
        ScoreResult score;
        try {
            String json = llmClient.generate(appId,
                    promptBuilder.buildScoreQuery(existing.getQuestionContent(), existing.getQuestionType(),
                            null, req.getAnswer()),
                    candidateId.toString(), timeout);
            score = parseScore(json);
        } catch (Exception e) {
            log.warn("评分失败，本题标记未评分: sessionId={}, q={}", sessionId, req.getQuestionNumber(), e);
            score = timeoutScore();
        }

        // 同题覆盖更新
        MockAnswer update = new MockAnswer();
        update.setSessionId(sessionId);
        update.setQuestionNumber(req.getQuestionNumber());
        update.setCandidateAnswer(req.getAnswer());
        update.setIsSkipped(0);
        update.setTechAccuracyScore(score.tech);
        update.setExpressionScore(score.expression);
        update.setKnowledgeDepthScore(score.depth);
        update.setOverallScore(score.overall);
        update.setAiComment(score.comment);
        update.setAnsweredAt(LocalDateTime.now());
        answerMapper.updateAnswer(update);

        MockAnswerResultVO vo = new MockAnswerResultVO();
        vo.setQuestionNumber(req.getQuestionNumber());
        vo.setTechAccuracyScore(score.tech);
        vo.setExpressionScore(score.expression);
        vo.setKnowledgeDepthScore(score.depth);
        vo.setOverallScore(score.overall);
        vo.setAiComment(score.comment);
        notify(listener, "result", vo);
    }

    // ==================== 跳过题目 ====================

    public void skip(String sessionId, Integer questionNumber, Long candidateId) {
        MockSession session = validateOwned(sessionId, candidateId);
        validateInProgress(session);

        MockAnswer existing = answerMapper.selectBySessionIdAndQuestionNumber(sessionId, questionNumber);
        if (existing == null) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_NOT_FOUND.getErrorCode(),
                    "题目不存在，请先重新生成面试");
        }

        MockAnswer update = new MockAnswer();
        update.setSessionId(sessionId);
        update.setQuestionNumber(questionNumber);
        update.setIsSkipped(1);
        update.setAnsweredAt(LocalDateTime.now());
        answerMapper.updateAnswer(update);
    }

    // ==================== 场景 C：生成报告 ====================

    public void report(String sessionId, Long candidateId, AgentEventListener listener) {
        MockSession session = validateOwned(sessionId, candidateId);
        if (STATUS_COMPLETED.equals(session.getStatus())) {
            // 已完成：幂等重看历史报告
            MockReport existed = reportMapper.selectBySessionId(sessionId);
            if (existed != null) {
                notify(listener, "result", toReportVO(existed));
                return;
            }
        }

        List<MockAnswer> answers = answerMapper.selectBySessionId(sessionId);
        List<MockAnswer> scored = answers.stream()
                .filter(a -> a.getOverallScore() != null)
                .collect(Collectors.toList());
        if (scored.isEmpty()) {
            throw new BusinessException(HrErrorCode.MOCK_NO_ANSWER_RECORD);
        }

        // 维度均分 + 总均分
        BigDecimal overall = avg(scored, a -> a.getOverallScore());
        BigDecimal tech = avg(scored, a -> a.getTechAccuracyScore());
        BigDecimal expression = avg(scored, a -> a.getExpressionScore());
        BigDecimal depth = avg(scored, a -> a.getKnowledgeDepthScore());
        BigDecimal project = avgProjectScore(scored);

        // LLM 生成报告（失败 → 规则兜底）
        String jobInfo = jobTool.fetch(session.getJobId(), session.getJobTitle());
        ReportResult rr;
        try {
            String appId = baibaoxiangProperties.getAgent().getReportAppId();
            String summary = buildQuestionsSummary(answers);
            String json = llmClient.generate(appId,
                    promptBuilder.buildReportQuery(jobInfo, summary, overall),
                    candidateId.toString(), hrAgentProperties.getLlm().getTimeoutMs());
            rr = parseReport(json);
            if (rr == null) {
                rr = ruleReport(overall);
            }
        } catch (Exception e) {
            log.warn("报告生成失败，走规则兜底: sessionId={}", sessionId, e);
            rr = ruleReport(overall);
        }

        // 写 mock_report
        LocalDateTime now = LocalDateTime.now();
        MockReport report = new MockReport();
        report.setSessionId(sessionId);
        report.setCandidateId(candidateId);
        report.setOverallScore(overall);
        report.setOverallLevel(levelOf(overall));
        report.setTechAccuracyScore(tech);
        report.setExpressionScore(expression);
        report.setKnowledgeDepthScore(depth);
        report.setProjectScore(project);
        report.setHighlights(toJsonList(rr.highlights));
        report.setWeaknesses(toJsonList(rr.weaknesses));
        report.setImprovementPlan(toJsonList(rr.improvementPlan));
        report.setTotalDurationSec((int) Duration.between(session.getStartedAt(), now).getSeconds());
        report.setAnsweredCount(scored.size());
        report.setSkippedCount((int) answers.stream().filter(a -> Integer.valueOf(1).equals(a.getIsSkipped())).count());
        report.setCreatedAt(now);
        reportMapper.insert(report);

        // 更新会话为 COMPLETED
        MockSession update = new MockSession();
        update.setId(session.getId());
        update.setStatus(STATUS_COMPLETED);
        update.setOverallScore(overall);
        update.setCompletedAt(now);
        sessionMapper.updateById(update);

        notify(listener, "result", toReportVO(report));
    }

    // ==================== 查询题目（继续面试） ====================

    public List<MockQuestionVO> getQuestions(String sessionId, Long candidateId) {
        validateOwned(sessionId, candidateId);
        List<MockAnswer> answers = answerMapper.selectBySessionId(sessionId);
        return answers.stream().map(a -> {
            MockQuestionVO vo = new MockQuestionVO();
            vo.setQuestionNumber(a.getQuestionNumber());
            vo.setQuestionType(a.getQuestionType());
            vo.setDimension(a.getQuestionDimension());
            vo.setDifficulty(null);
            vo.setContent(a.getQuestionContent());
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    private List<MockQuestionVO> generateQuestions(MockGenerateRequest req, Long candidateId,
                                                   String jobInfo, String resumeInfo, int count) {
        String appId = baibaoxiangProperties.getAgent().getGenerateAppId();
        try {
            String json = llmClient.generate(appId,
                    promptBuilder.buildGenerateQuery(jobInfo, resumeInfo, count),
                    candidateId.toString(), hrAgentProperties.getLlm().getTimeoutMs());
            return parseQuestions(json, count);
        } catch (Exception e) {
            log.warn("出题 LLM 失败，走内置模板: jobId={}", req.getJobId(), e);
            return buildFallbackQuestions(count);
        }
    }

    private String genSessionId() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "mock:session:seq:" + date;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            seq = 1L;
        }
        return "mock-" + date + "-" + String.format("%03d", seq);
    }

    private int normalizeCount(Integer questionCount) {
        if (questionCount != null && (questionCount == 8 || questionCount == 10)) {
            return questionCount;
        }
        return 5;
    }

    private MockSession validateOwned(String sessionId, Long candidateId) {
        if (sessionId == null) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_NOT_FOUND);
        }
        MockSession session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_NOT_FOUND);
        }
        if (!candidateId.equals(session.getCandidateId())) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_NOT_FOUND);
        }
        return session;
    }

    private void validateInProgress(MockSession session) {
        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new BusinessException(HrErrorCode.MOCK_SESSION_COMPLETED);
        }
    }

    /**
     * 每日次数校验（hr.mock-interview.quota，0=不限）。
     * 按当天已创建的会话数计数，达到上限抛 40018。
     */
    private void checkQuota(Long candidateId) {
        int quota = mockInterviewProperties.getQuota();
        if (quota <= 0) {
            return;
        }
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        int todayCount = sessionMapper.countByCandidateIdAndStartAt(candidateId, startOfToday);
        if (todayCount >= quota) {
            throw new BusinessException(HrErrorCode.MOCK_QUOTA_EXCEEDED);
        }
    }

    // ==================== LLM 输出解析 ====================

    private List<MockQuestionVO> parseQuestions(String json, int expected) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("questions");
            List<MockQuestionVO> list = new ArrayList<>();
            if (arr.isArray()) {
                int i = 1;
                for (JsonNode n : arr) {
                    String content = n.path("content").asText("");
                    if (content.isEmpty()) {
                        continue;
                    }
                    MockQuestionVO vo = new MockQuestionVO();
                    vo.setQuestionNumber(n.path("questionNumber").isInt()
                            ? n.path("questionNumber").asInt() : i);
                    vo.setQuestionType(n.path("questionType").asText("BASIC"));
                    vo.setDimension(n.path("dimension").asText(""));
                    vo.setDifficulty(n.path("difficulty").asText("MEDIUM"));
                    vo.setContent(content);
                    list.add(vo);
                    i++;
                }
            }
            if (list.isEmpty()) {
                return buildFallbackQuestions(expected);
            }
            if (list.size() > expected) {
                return list.subList(0, expected);
            }
            if (list.size() < expected) {
                // 少则补齐兜底模板题（工作流设计 §2.5），补题接在已返回题目之后
                List<MockQuestionVO> padding = buildFallbackQuestions(expected - list.size());
                int start = list.size();
                for (int i = 0; i < padding.size(); i++) {
                    MockQuestionVO f = padding.get(i);
                    f.setQuestionNumber(start + i + 1);
                    list.add(f);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("题目 JSON 解析失败: {}", json, e);
            return buildFallbackQuestions(expected);
        }
    }

    private ScoreResult parseScore(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            int tech = clamp(n.path("techAccuracyScore").asInt(0));
            int expression = clamp(n.path("expressionScore").asInt(0));
            int depth = clamp(n.path("knowledgeDepthScore").asInt(0));
            String comment = n.path("aiComment").asText("");
            BigDecimal overall = BigDecimal.valueOf(tech * 0.5 + expression * 0.3 + depth * 0.2)
                    .setScale(1, RoundingMode.HALF_UP);
            return new ScoreResult(BigDecimal.valueOf(tech), BigDecimal.valueOf(expression),
                    BigDecimal.valueOf(depth), overall, comment);
        } catch (Exception e) {
            log.error("评分 JSON 解析失败: {}", json, e);
            return timeoutScore();
        }
    }

    private ScoreResult timeoutScore() {
        return new ScoreResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "评分服务繁忙，本题未评分");
    }

    private ReportResult parseReport(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            return new ReportResult(toStringList(n.path("highlights")),
                    toStringList(n.path("weaknesses")),
                    toStringList(n.path("improvementPlan")));
        } catch (Exception e) {
            log.error("报告 JSON 解析失败: {}", json, e);
            return null;
        }
    }

    private ReportResult ruleReport(BigDecimal overall) {
        double score = overall == null ? 0 : overall.doubleValue();
        List<String> highlights = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> plan = new ArrayList<>();
        if (score >= 80) {
            highlights.add("整体表现优秀，对考察内容掌握扎实");
        } else if (score >= 60) {
            highlights.add("整体表现合格，具备基本的技术深度");
        } else {
            weaknesses.add("整体得分偏低，核心技术能力需要加强");
        }
        weaknesses.add("部分题目回答深度不足，建议补充原理级理解");
        plan.add("针对薄弱知识点系统复习并输出笔记");
        plan.add("练习限时作答，提升答题节奏");
        plan.add("学习 STAR 法则组织面试表达");
        return new ReportResult(highlights, weaknesses, plan);
    }

    // ==================== 工具方法 ====================

    private BigDecimal avg(List<MockAnswer> scored, java.util.function.Function<MockAnswer, BigDecimal> getter) {
        BigDecimal sum = scored.stream().map(getter).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cnt = scored.stream().map(getter).filter(java.util.Objects::nonNull).count();
        return cnt == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(cnt), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal avgProjectScore(List<MockAnswer> scored) {
        List<MockAnswer> project = scored.stream()
                .filter(a -> "PROJECT".equals(a.getQuestionType()))
                .collect(Collectors.toList());
        if (project.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return avg(project, MockAnswer::getOverallScore);
    }

    private String levelOf(BigDecimal overall) {
        double score = overall == null ? 0 : overall.doubleValue();
        if (score >= 85) return "EXCELLENT";
        if (score >= 70) return "GOOD";
        if (score >= 60) return "AVERAGE";
        return "NEED_IMPROVE";
    }

    private String buildQuestionsSummary(List<MockAnswer> answers) {
        return answers.stream()
                .map(a -> "题目" + a.getQuestionNumber() + "(" + a.getQuestionType()
                        + (a.getOverallScore() != null ? "," + a.getOverallScore() + "分" : ",未评分") + "): "
                        + truncate(a.getQuestionContent(), 120)
                        + (a.getAiComment() != null ? " 点评:" + truncate(a.getAiComment(), 60) : ""))
                .collect(Collectors.joining("\n"));
    }

    private List<MockQuestionVO> buildFallbackQuestions(int count) {
        String[][] templates = {
                {"BASIC", "基础验证", "EASY", "请解释进程与线程的区别，并说明各自的使用场景。"},
                {"BASIC", "基础验证", "MEDIUM", "请解释闭包的概念，并说明其常见用途与潜在的内存泄漏风险。"},
                {"PROJECT", "项目深挖", "HARD", "描述一次你解决过的线上性能问题，从定位到解决的全过程。"},
                {"BOUNDARY", "能力边界", "HARD", "假设系统需要支持每秒十万次写入，你会如何设计存储方案？"},
                {"COMPREHENSIVE", "综合素养", "MEDIUM", "当你的技术方案与团队主流意见冲突时，你会如何处理？"}
        };
        List<MockQuestionVO> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] t = templates[i % templates.length];
            MockQuestionVO vo = new MockQuestionVO();
            vo.setQuestionNumber(i + 1);
            vo.setQuestionType(t[0]);
            vo.setDimension(t[1]);
            vo.setDifficulty(t[2]);
            vo.setContent(t[3]);
            list.add(vo);
        }
        return list;
    }

    private List<String> toStringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(n -> list.add(n.asText("")));
        }
        return list;
    }

    private String toJsonList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? new ArrayList<>() : list);
        } catch (Exception e) {
            log.warn("列表序列化失败", e);
            return "[]";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private Map<String, Object> progress(int sequence, String code, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sequence", sequence);
        map.put("code", code);
        map.put("message", message);
        return map;
    }

    private Map<String, Object> doneData() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        return map;
    }

    private MockReportVO toReportVO(MockReport r) {
        MockReportVO vo = new MockReportVO();
        vo.setSessionId(r.getSessionId());
        vo.setOverallScore(r.getOverallScore());
        vo.setOverallLevel(r.getOverallLevel());
        vo.setTechAccuracyScore(r.getTechAccuracyScore());
        vo.setExpressionScore(r.getExpressionScore());
        vo.setKnowledgeDepthScore(r.getKnowledgeDepthScore());
        vo.setProjectScore(r.getProjectScore());
        vo.setHighlights(parseJsonArray(r.getHighlights()));
        vo.setWeaknesses(parseJsonArray(r.getWeaknesses()));
        vo.setImprovementPlan(parseJsonArray(r.getImprovementPlan()));
        vo.setAnsweredCount(r.getAnsweredCount());
        vo.setSkippedCount(r.getSkippedCount());
        vo.setTotalDurationSec(r.getTotalDurationSec());
        return vo;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("JSON 数组解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    private void notify(AgentEventListener listener, String event, Object data) {
        if (listener != null) {
            try {
                listener.onEvent(event, data);
            } catch (Exception e) {
                log.debug("Agent 事件推送失败: event={}", event, e);
            }
        }
    }

    // ==================== 内部类 ====================

    /** Agent 事件监听接口（SSE 推送回调） */
    @FunctionalInterface
    public interface AgentEventListener {
        void onEvent(String eventName, Object data);
    }

    /** 评分结果 */
    @RequiredArgsConstructor
    private static class ScoreResult {
        final BigDecimal tech;
        final BigDecimal expression;
        final BigDecimal depth;
        final BigDecimal overall;
        final String comment;
    }

    /** 报告生成结果 */
    @RequiredArgsConstructor
    private static class ReportResult {
        final List<String> highlights;
        final List<String> weaknesses;
        final List<String> improvementPlan;
    }
}

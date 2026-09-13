package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import com.lingxi.job.agent.BaibaoxiangUserIdProvider;
import com.lingxi.job.agent.JdPromptSanitizer;
import com.lingxi.job.domain.dto.request.JdParseRequest;
import com.lingxi.job.domain.dto.response.JdParseResponse;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.service.AiParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * JD 解析服务实现
 * <p>
 * 逻辑（系分 F-09 + 差异表 #1/#4）：
 * <ol>
 *   <li>校验 JD 非空、≤20000（@Valid + @Size，Controller 层）</li>
 *   <li>调百宝箱 Agent API（结构化规则由百宝箱工作流提示词负责，后端只透传脱敏 JD）</li>
 *   <li>超时 30s → {@code AI_PARSE_UNAVAILABLE}(2301)</li>
 *   <li>JSON 解析失败 → 重试 1 次（prompt 追加提示）→ 仍失败 → 500</li>
 *   <li>结果校验：禁性别/年龄/民族 → 400 reject；薪资无法识别 → rawText + warnings</li>
 *   <li>返回画像草稿（hiddenRequirements inferred=true、hrConfirmed=false）</li>
 * </ol>
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiParseServiceImpl implements AiParseService {

    /** JSON 解析失败后的最大重试次数 */
    private static final int MAX_RETRY = 1;

    /** 禁性别/年龄/民族关键词（JD 内容合规校验） */
    private static final List<String> FORBIDDEN_KEYWORDS = Arrays.asList(
            "男", "女", "性别", "汉族", "民族", "年龄",
            "周岁", "岁");

    /** 薪资识别关键词（用于提取原始薪资文本） */
    private static final Pattern SALARY_PATTERN = Pattern.compile(
            ".*(?:薪资|月薪|年薪|待遇|[0-9]+\\s*[kKwW万]|[0-9]+\\s*[-~至到]\\s*[0-9]+).*");

    private final BaibaoxiangAgentClient baibaoxiangAgentClient;
    private final ObjectMapper objectMapper;
    private final BaibaoxiangUserIdProvider userIdProvider;
    private final JdPromptSanitizer jdPromptSanitizer;

    /** JD 解析超时（秒），读取自配置 */
    @Value("${ai.agent.jd-parse-timeout-seconds:30}")
    private long timeoutSeconds;

    /** 目标百宝箱应用 ID（JD 解析，Real 模式使用；Mock 模式不校验） */
    @Value("${baibaoxiang.jd-parse-app-id:}")
    private String jdParseAppId;

    /**
     * 解析任务线程池（有界：core=2/max=4/queue=20/AbortPolicy，用于 Future.get 超时控制）
     * <p>单线程会在一个不可中断的网络挂起时卡死全部 JD 请求；有界池 + 拒绝策略 → 池满转 2301。</p>
     */
    private ExecutorService parseExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            r -> {
                Thread t = new Thread(r, "ai-jd-parse");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * 应用销毁时关闭解析线程池（防止 daemon 线程残留）
     */
    @PreDestroy
    public void shutdown() {
        parseExecutor.shutdownNow();
    }

    @Override
    public JdParseResponse parseJd(JdParseRequest request) {
        // 请求线程捕获 HR 用户 ID → 按 AppID 隔离的百宝箱伪标识（异步线程不读 UserContext）
        Long internalUserId = UserContext.getUserId();
        if (internalUserId == null) {
            throw new BusinessException(401, "缺少用户上下文");
        }
        String userId = userIdProvider.provide(internalUserId, jdParseAppId);
        // JD 出站前清洗（限长 + 电话/邮箱/身份证/精确地址脱敏），原文不进日志
        String jdText = jdPromptSanitizer.sanitize(request.getJdText());
        String prompt = buildPrompt(jdText);
        String json = callAgent(prompt, userId);
        try {
            return parseAndValidate(json, jdText);
        } catch (JsonProcessingException e) {
            // 第一次解析格式错误 → 重试 1 次
            log.warn("JD 解析输出格式错误，重试 1 次: {}", e.getMessage());
            String retryJson = callAgent(prompt + "\n\n（提示：上次输出格式错误，请重新解析并输出合法 JSON 结果）", userId);
            try {
                // 与首路径一致：只传脱敏后的 JD，防止原始敏感片段进入薪资 rawText
                return parseAndValidate(retryJson, jdText);
            } catch (JsonProcessingException e2) {
                log.error("JD 解析重试后仍格式错误", e2);
                throw new BusinessException(500, "JD解析结果格式错误");
            }
        }
    }

    /**
     * 调用百宝箱并等待结果，超时 → 2301
     *
     * @param prompt 解析 Prompt
     * @return 画像 JSON 字符串
     */
    private String callAgent(String prompt, String userId) {
        Future<String> future;
        try {
            future = parseExecutor.submit(() -> baibaoxiangAgentClient.parseJd(prompt, userId));
        } catch (RejectedExecutionException e) {
            // 线程池满 → 视为 AI 不可用（2301），避免请求无限排队
            log.error("JD 解析线程池拒绝（队列满）", e);
            throw new BusinessException(JobErrorCode.AI_PARSE_UNAVAILABLE);
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("JD 解析超时（{}s）", timeoutSeconds);
            throw new BusinessException(JobErrorCode.AI_PARSE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(JobErrorCode.AI_PARSE_UNAVAILABLE.getErrorCode(),
                    "JD解析AI不可用", e);
        } catch (Exception e) {
            throw new BusinessException(JobErrorCode.AI_PARSE_UNAVAILABLE.getErrorCode(),
                    "JD解析AI不可用", e);
        }
    }

    /**
     * 解析并校验画像 JSON
     */
    private JdParseResponse parseAndValidate(String json, String jdText) throws JsonProcessingException {
        // 1. 禁性别/年龄/民族校验（对原始 JSON 文本检测，防 Agent 输出绕过）
        assertNoForbiddenInfo(json);

        JdParseResponse resp = objectMapper.readValue(json, JdParseResponse.class);
        if (resp.getWarnings() == null) {
            resp.setWarnings(new ArrayList<>());
        }

        // 2. 隐性要求：inferred=true、hrConfirmed=false（画像草稿阶段）
        if (resp.getHiddenRequirements() != null) {
            resp.getHiddenRequirements().forEach(h -> {
                h.setInferred(true);
                h.setHrConfirmed(false);
            });
        }

        // 3. 薪资降级：无法结构化识别 → rawText + warnings
        if (resp.getSalary() == null
                || (resp.getSalary().getMinAmount() == null && resp.getSalary().getMaxAmount() == null)) {
            JdParseResponse.Salary salary = new JdParseResponse.Salary();
            salary.setRawText(extractSalaryText(jdText));
            resp.setSalary(salary);
            resp.getWarnings().add("薪资无法结构化识别，已保留原文");
        }

        return resp;
    }

    /**
     * 禁性别/年龄/民族校验：命中关键词 → 400 reject
     */
    private void assertNoForbiddenInfo(String json) {
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (json != null && json.contains(keyword)) {
                log.warn("JD 解析结果包含违规信息[{}]，拒绝采用", keyword);
                throw new BusinessException(400, "JD包含性别/年龄/民族等歧视信息，请修改后重试");
            }
        }
    }

    /**
     * 从 JD 原文提取薪资文本片段（识别失败返回 null）
     */
    private String extractSalaryText(String jdText) {
        if (jdText == null) {
            return null;
        }
        java.util.regex.Matcher matcher = SALARY_PATTERN.matcher(jdText);
        // 简化：返回第一个包含薪资关键词的行
        for (String line : jdText.split("\\n")) {
            if (SALARY_PATTERN.matcher(line).matches()) {
                return line.trim().length() > 128 ? line.trim().substring(0, 128) : line.trim();
            }
        }
        return null;
    }

    /**
     * 组装百宝箱 JD 解析请求内容
     * <p>
     * 结构化规则（字段结构、jobType 枚举、hiddenRequirements 五字段、薪资周期等）由
     * <b>百宝箱工作流提示词</b>作为唯一来源，后端不再内嵌 JSON Schema 与约束，只透传
     * 已脱敏 JD，避免双提示词冲突（如 jobType 兜底 GENERAL→OTHER、hiddenRequirements 缺
     * inferred/hrConfirmed、薪资周期 YEAR→MONTH）。
     * </p>
     */
    private String buildPrompt(String jdText) {
        return jdText;
    }
}

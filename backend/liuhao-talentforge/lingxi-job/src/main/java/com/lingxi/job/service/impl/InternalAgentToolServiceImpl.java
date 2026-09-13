package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.agent.AgentContextStore;
import com.lingxi.job.agent.AgentRunContext;
import com.lingxi.job.domain.dto.query.QuestionSearchQuery;
import com.lingxi.job.domain.vo.QuestionPromptVO;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.mapper.JobQuestionMapper;
import com.lingxi.job.service.InternalAgentToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 百宝箱 Agent Tool 服务实现（阶段4）
 * <p>
 * <b>companyId 一致性</b>：runToken 即数据隔离边界——所有数据访问（画像/题库）
 * 一律用 {@code context.companyId} 查询（数据库隔离），业务 ID（jobId/appId）必须与
 * context 绑定一致（Tool1/Tool2 各自校验），故无需外部 companyId 入参。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalAgentToolServiceImpl implements InternalAgentToolService {

    private final AgentContextStore agentContextStore;
    private final JobProfileMapper jobProfileMapper;
    private final JobQuestionMapper jobQuestionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getJobRequirements(String runToken, Long jobId) {
        AgentRunContext context = validateRunToken(runToken);
        // 跨企业/跨岗位拒绝：jobId 必须与 runToken 绑定一致
        if (context.getJobId() == null || !context.getJobId().equals(jobId)) {
            throw new BusinessException(401, "运行令牌与岗位不匹配");
        }
        JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, context.getCompanyId());
        if (profile == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobType", profile.getJobType());
        result.put("coreSkills", parseJsonArray(profile.getCoreSkills()));
        result.put("softSkills", parseJsonArray(profile.getSoftSkills()));
        result.put("interviewFocus", parseJsonArray(profile.getInterviewFocus()));
        return result;
    }

    @Override
    public List<String> getHighlights(String runToken, Long appId) {
        AgentRunContext context = validateRunToken(runToken);
        if (context.getApplicationId() == null || !context.getApplicationId().equals(appId)) {
            throw new BusinessException(401, "运行令牌与投递记录不匹配");
        }
        // 低完整度（非个性化）不提供简历亮点（防御：与 generate 空列表一致，跳过简历上下文闭环）
        if (!Boolean.TRUE.equals(context.getPersonalized())) {
            return Collections.emptyList();
        }
        List<String> highlights = context.getResumeHighlights();
        return highlights == null ? new ArrayList<>() : highlights;
    }

    @Override
    public List<QuestionPromptVO> searchQuestions(String runToken, String skillTags,
                                                          String questionType, String difficulty) {
        AgentRunContext context = validateRunToken(runToken);

        QuestionSearchQuery query = new QuestionSearchQuery();
        query.setCompanyId(context.getCompanyId());
        query.setJobType(context.getJobType());
        query.setQuestionType(questionType);
        query.setDifficulty(difficulty);
        if (StringUtils.hasText(skillTags)) {
            query.setSkillTags(Arrays.stream(skillTags.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        }

        List<JobQuestion> questions = jobQuestionMapper.selectForAgentSearch(query);
        return questions.stream().map(this::toProjection).collect(Collectors.toList());
    }

    // ==================== 私有辅助 ====================

    /**
     * runToken 校验（Tool 公共流程）
     * <p>
     * <b>companyId 一致性语义</b>：runToken 即数据隔离边界——所有数据访问（Tool1 画像、
     * Tool3 题库）一律用 {@code context.companyId} 查询（数据库隔离），业务 ID
     * （jobId/appId）必须与 context 绑定一致（Tool1/Tool2 各自校验），故无需外部
     * companyId 入参（无来源）。校验步骤：读头 → get context → 校验存在 → 校验未过期。
     * </p>
     */
    private AgentRunContext validateRunToken(String runToken) {
        if (!StringUtils.hasText(runToken)) {
            throw new BusinessException(401, "缺少运行令牌");
        }
        AgentRunContext context = agentContextStore.get(runToken);
        if (context == null) {
            throw new BusinessException(401, "运行令牌无效或已过期");
        }
        if (context.getExpiresAt() == null || context.getExpiresAt().isBefore(LocalDateTime.now())) {
            agentContextStore.remove(runToken);
            throw new BusinessException(401, "运行令牌已过期");
        }
        return context;
    }

    /** 组装题库投影（剔除 referenceAnswer/id/companyId） */
    private QuestionPromptVO toProjection(JobQuestion q) {
        QuestionPromptVO p = new QuestionPromptVO();
        p.setQuestionType(q.getQuestionType());
        p.setDifficulty(q.getDifficulty());
        p.setContent(q.getContent());
        p.setKeyPoints(q.getKeyPoints());
        p.setContentHash(q.getContentSha256());
        p.setNormalizedSkillTags(parseStringList(q.getSkillTags()));
        p.setSanitizedAbilityPoints(parseAbilityPoints(q));
        return p;
    }

    /**
     * 结构化能力点：优先从 evaluation_points JSON 解析（数组/对象 → 紧凑 JSON 字符串），
     * 为空或解析失败回退 keyPoints（与 DTO 字段注释一致）
     */
    private String parseAbilityPoints(JobQuestion q) {
        String ev = q.getEvaluationPoints();
        if (StringUtils.hasText(ev)) {
            try {
                JsonNode node = objectMapper.readTree(ev);
                if (node.isArray() || node.isObject()) {
                    return node.toString();
                }
                return node.asText();
            } catch (Exception e) {
                log.warn("evaluation_points 解析失败，回退 keyPoints: questionId={}", q.getId(), e);
            }
        }
        return q.getKeyPoints();
    }

    /** 解析 String JSON 为对象数组 */
    private List<Object> parseJsonArray(String json) {
        return parseJsonList(json, new TypeReference<List<Object>>() { });
    }

    /** 解析 String JSON 为字符串列表 */
    private List<String> parseStringList(String json) {
        return parseJsonList(json, new TypeReference<List<String>>() { });
    }

    /**
     * 通用 JSON 数组解析（解析失败/空值返回空列表）
     */
    private <T> List<T> parseJsonList(String json, TypeReference<List<T>> type) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("JSON 数组解析失败", e);
            return new ArrayList<>();
        }
    }
}

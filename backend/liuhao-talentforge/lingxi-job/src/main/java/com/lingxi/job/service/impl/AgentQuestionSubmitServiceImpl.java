package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.domain.dto.EvaluationPointDTO;
import com.lingxi.job.domain.dto.request.AgentQuestionSubmitRequest;
import com.lingxi.job.domain.dto.response.AgentQuestionSubmitResponse;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.enums.QuestionSourceEnum;
import com.lingxi.job.enums.QuestionStatusEnum;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.mapper.JobQuestionMapper;
import com.lingxi.job.service.AgentQuestionSubmitService;
import com.lingxi.job.agent.QuestionContentSanitizer;
import com.lingxi.job.validator.QuestionValidator;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面试官提交 AI 面试题申请入企业题库服务实现（阶段6.3 二期）
 * <p>companyId/createdBy/source/status 一律从 UserContext 取/后端强制，不信任前端；
 * jobType/skillTags 由岗位画像推导。去重/软删恢复/插入复用既有 JobQuestionMapper 写侧方法，
 * 与 {@link HrQuestionServiceImpl#createQuestion} 语义一致（同企业同内容 → 2404）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentQuestionSubmitServiceImpl implements AgentQuestionSubmitService {

    private final JobPostMapper jobPostMapper;
    private final JobProfileMapper jobProfileMapper;
    private final JobQuestionMapper jobQuestionMapper;
    private final QuestionContentSanitizer questionContentSanitizer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentQuestionSubmitResponse submitToLibrary(Long jobId, AgentQuestionSubmitRequest request) {
        // ① 上下文强制真值（不信任前端）
        Long companyId = UserContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException(403, "缺少企业上下文");
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "缺少用户上下文");
        }

        // ② 岗位归属校验（企业隔离，跨企业/不存在 → 2101）
        JobPost post = jobPostMapper.selectByIdAndCompanyId(jobId, companyId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }

        // ③ 画像推导归档维度 job_type / skill_tags（后端取，不信任前端）
        JobProfile profile = jobProfileMapper.selectByJobIdAndCompanyId(jobId, companyId);
        String jobType = profile == null ? null : profile.getJobType();
        if (!StringUtils.hasText(jobType)) {
            throw new BusinessException(400, "岗位画像未确认，无法归档题目");
        }
        List<String> skillTags = profile == null ? null : parseSkillTags(profile.getCoreSkills());

        // ④ 校验 + 规范化（复用 QuestionValidator）
        String rawContent = QuestionValidator.normalizeContent(request.getContent());
        QuestionValidator.validateQuestionType(request.getQuestionType());
        String difficulty = QuestionValidator.normalizeDifficulty(request.getDifficulty());
        QuestionValidator.validateEvaluationPoints(request.getEvaluationDimensions());
        // keyPoints 已改 List<String>，原始长度预检由脱敏后拼接串在 ⑤ 兜底；此处仅预检参考答案
        QuestionValidator.validateOptionalText(null, request.getReferenceAnswer());

        // ⑤ 二次脱敏（候选人姓名/公司/项目/学校/联系方式），脱敏后兜底校验
        String content = QuestionValidator.normalizeContent(questionContentSanitizer.sanitize(rawContent));
        String keyPoints = sanitizeAndJoinKeyPoints(request.getKeyPoints());
        String referenceAnswer = request.getReferenceAnswer() == null ? null
                : questionContentSanitizer.sanitize(request.getReferenceAnswer());
        QuestionValidator.validateOptionalText(keyPoints, referenceAnswer);
        List<EvaluationPointDTO> evaluationPoints = sanitizeEvaluationPoints(request.getEvaluationDimensions());
        if (evaluationPoints != null && evaluationPoints.isEmpty()) {
            evaluationPoints = null;
        }

        // ⑥ 组装 JobQuestion：source/status/createdBy 强制
        JobQuestion q = new JobQuestion();
        q.setCompanyId(companyId);
        q.setJobType(jobType);
        q.setQuestionType(request.getQuestionType());
        q.setDifficulty(difficulty);
        q.setContent(content);
        q.setContentSha256(sha256(content));
        q.setSkillTags(toJsonOrNull(skillTags));
        q.setKeyPoints(keyPoints);
        q.setReferenceAnswer(referenceAnswer);
        q.setEvaluationPoints(toJsonOrNull(evaluationPoints));
        q.setSource(QuestionSourceEnum.AI_GENERATED.getCode());
        q.setStatus(QuestionStatusEnum.PENDING_REVIEW.getCode());
        q.setCreatedBy(userId);

        // ⑦ 去重（未删除行→2404）/软删恢复复用/插入，与 HrQuestionServiceImpl.createQuestion 一致
        try {
            int active = jobQuestionMapper.countActiveByCompanyAndContentHash(companyId, q.getContentSha256(), null);
            if (active > 0) {
                throw new BusinessException(JobErrorCode.QUESTION_DUPLICATE_CONTENT);
            }
            JobQuestion deleted = jobQuestionMapper.selectDeletedByContentHash(companyId, q.getContentSha256());
            if (deleted != null) {
                int rows = jobQuestionMapper.restoreDeleted(deleted.getId(), companyId, q);
                if (rows == 0) {
                    throw new BusinessException(JobErrorCode.QUESTION_DUPLICATE_CONTENT);
                }
                q.setId(deleted.getId());
            } else {
                jobQuestionMapper.insert(q);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(JobErrorCode.QUESTION_DUPLICATE_CONTENT);
        }
        return new AgentQuestionSubmitResponse(q.getId(),
                QuestionStatusEnum.PENDING_REVIEW.getCode(), "已提交题库审核");
    }

    // ==================== 私有辅助（复刻 HrQuestionServiceImpl/InterviewAgentServiceImpl 同构） ====================

    /** 题干 SHA-256（小写 hex，对齐 CHAR(64)） */
    private String sha256(String content) {
        return SecureUtil.sha256(content);
    }

    /** 对象 → JSON 字符串；null → null */
    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JsonUtil.getMapper().writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化 JSON 失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    /** core_skills JSON → 技能名称列表（结构 [{name,...}]，同 InterviewAgentServiceImpl.parseSkillTags） */
    private List<String> parseSkillTags(String coreSkillsJson) {
        if (!StringUtils.hasText(coreSkillsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> skills = JsonUtil.getMapper().readValue(coreSkillsJson,
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

    /** 评分要点逐项脱敏 name（防评分要点含企业/人名）；null/空保持原样 */
    private List<EvaluationPointDTO> sanitizeEvaluationPoints(List<EvaluationPointDTO> points) {
        if (points == null || points.isEmpty()) {
            return points;
        }
        List<EvaluationPointDTO> list = new ArrayList<>(points.size());
        for (EvaluationPointDTO p : points) {
            EvaluationPointDTO copy = new EvaluationPointDTO();
            copy.setName(p.getName() == null ? null : questionContentSanitizer.sanitize(p.getName()));
            copy.setWeight(p.getWeight());
            list.add(copy);
        }
        return list;
    }

    /** 单条考察要点最大长度（拼接总长 ≤1000 由 QuestionValidator.validateOptionalText 兜底） */
    private static final int MAX_SINGLE_KEY_POINT_LENGTH = 500;

    /**
     * 考察要点 List → 落库单字符串：null/空白项跳过 → trim → 逐条二次脱敏 → 单条限长 → 换行拼接。
     * 空/全空 → null。兼容 SSE result 数组与单字符串两形态（DTO 侧已统一为 List）。
     */
    private String sanitizeAndJoinKeyPoints(List<String> keyPoints) {
        if (keyPoints == null || keyPoints.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>(keyPoints.size());
        for (String kp : keyPoints) {
            if (!StringUtils.hasText(kp)) {
                continue;
            }
            String sanitized = questionContentSanitizer.sanitize(kp.trim());
            if (sanitized.length() > MAX_SINGLE_KEY_POINT_LENGTH) {
                throw new BusinessException(400, "单条考察要点不能超过 " + MAX_SINGLE_KEY_POINT_LENGTH + " 字符");
            }
            cleaned.add(sanitized);
        }
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }
}

package com.lingxi.job.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.job.domain.dto.EvaluationPointDTO;
import com.lingxi.job.domain.dto.query.QuestionListQuery;
import com.lingxi.job.domain.dto.request.QuestionCreateRequest;
import com.lingxi.job.domain.dto.request.QuestionReviewRequest;
import com.lingxi.job.domain.dto.request.QuestionStatusRequest;
import com.lingxi.job.domain.dto.request.QuestionUpdateRequest;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.domain.vo.HrQuestionDetailVO;
import com.lingxi.job.domain.vo.HrQuestionListVO;
import com.lingxi.job.enums.DifficultyEnum;
import com.lingxi.job.enums.QuestionSourceEnum;
import com.lingxi.job.enums.QuestionStatusEnum;
import com.lingxi.job.enums.QuestionTypeEnum;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobQuestionMapper;
import com.lingxi.job.service.HrQuestionService;
import com.lingxi.job.validator.QuestionValidator;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业私有题库管理服务实现（B端 HR，阶段6.1）
 * <p>companyId 一律从 UserContext 取，不信任前端；所有写操作乐观锁 version。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrQuestionServiceImpl implements HrQuestionService {

    private final JobQuestionMapper jobQuestionMapper;

    /** 可编辑状态 */
    private static final List<String> EDITABLE_STATUS = Collections.unmodifiableList(
            Arrays.asList("ACTIVE", "INACTIVE", "REJECTED"));

    /** 列表难度白名单校验（不用 DifficultyEnum.normalize 防 null→MEDIUM） */
    private static boolean isValidDifficulty(String difficulty) {
        for (DifficultyEnum d : DifficultyEnum.values()) {
            if (d.getCode().equals(difficulty)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<HrQuestionListVO> listQuestions(QuestionListQuery query) {
        Long companyId = requireCompanyId();
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();
        validatePage(page, size);
        // 筛选参数合法性：questionType/status 非法 → 400；difficulty 传了校验、未传不过滤
        if (StringUtils.hasText(query.getQuestionType()) && !QuestionTypeEnum.isValid(query.getQuestionType())) {
            throw new BusinessException(400, "题目类型编码不合法");
        }
        if (StringUtils.hasText(query.getDifficulty()) && !isValidDifficulty(query.getDifficulty())) {
            throw new BusinessException(400, "非法的难度值: " + query.getDifficulty());
        }
        if (StringUtils.hasText(query.getStatus()) && !QuestionStatusEnum.isValid(query.getStatus())) {
            throw new BusinessException(400, "非法题目状态: " + query.getStatus());
        }

        PageHelper.startPage(page, size);
        List<JobQuestion> list = jobQuestionMapper.selectHrQuestionList(companyId, query);
        PageInfo<JobQuestion> pageInfo = new PageInfo<>(list);
        List<HrQuestionListVO> voList = list.stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(voList, pageInfo.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public HrQuestionDetailVO getQuestionDetail(Long id) {
        Long companyId = requireCompanyId();
        JobQuestion q = jobQuestionMapper.selectByIdAndCompanyId(id, companyId);
        if (q == null) {
            throw new BusinessException(JobErrorCode.QUESTION_NOT_FOUND);
        }
        return toDetailVO(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrQuestionDetailVO createQuestion(QuestionCreateRequest request) {
        Long companyId = requireCompanyId();
        Long userId = UserContext.getUserId();

        // 校验 + 规范化
        String content = QuestionValidator.normalizeContent(request.getContent());
        QuestionValidator.validateQuestionType(request.getQuestionType());
        String difficulty = QuestionValidator.normalizeDifficulty(request.getDifficulty());
        List<String> skillTags = QuestionValidator.normalizeSkillTags(request.getSkillTags());
        QuestionValidator.validateEvaluationPoints(request.getEvaluationPoints());
        QuestionValidator.validateOptionalText(request.getKeyPoints(), request.getReferenceAnswer());
        // 评分要点空数组等价"无评分要点"→ null（存 NULL，对齐契约"null=无评分要点"）
        List<EvaluationPointDTO> evaluationPoints = request.getEvaluationPoints();
        if (evaluationPoints != null && evaluationPoints.isEmpty()) {
            evaluationPoints = null;
        }

        JobQuestion q = new JobQuestion();
        q.setCompanyId(companyId);
        q.setJobType(request.getJobType().trim());
        q.setQuestionType(request.getQuestionType());
        q.setDifficulty(difficulty);
        q.setContent(content);
        q.setContentSha256(sha256(content));
        q.setSkillTags(toJsonOrNull(skillTags));
        q.setKeyPoints(request.getKeyPoints());
        q.setReferenceAnswer(request.getReferenceAnswer());
        q.setEvaluationPoints(toJsonOrNull(evaluationPoints));
        q.setSource(QuestionSourceEnum.HR_CREATED.getCode());
        q.setStatus(QuestionStatusEnum.ACTIVE.getCode());
        q.setCreatedBy(userId);

        try {
            // 去重：未删除行 → 2404；软删行 → 恢复复用（解决 uk_question_content 撞索引）
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
            // 并发恢复/插入撞唯一索引 → 重复
            throw new BusinessException(JobErrorCode.QUESTION_DUPLICATE_CONTENT);
        }
        return toDetailVO(jobQuestionMapper.selectByIdAndCompanyId(q.getId(), companyId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrQuestionDetailVO updateQuestion(Long id, QuestionUpdateRequest request) {
        Long companyId = requireCompanyId();
        JobQuestion existing = jobQuestionMapper.selectByIdAndCompanyId(id, companyId);
        if (existing == null) {
            throw new BusinessException(JobErrorCode.QUESTION_NOT_FOUND);
        }
        if (!EDITABLE_STATUS.contains(existing.getStatus())) {
            throw new BusinessException(JobErrorCode.QUESTION_ILLEGAL_STATE);
        }

        String content = QuestionValidator.normalizeContent(request.getContent());
        QuestionValidator.validateQuestionType(request.getQuestionType());
        String difficulty = QuestionValidator.normalizeDifficulty(request.getDifficulty());
        List<String> skillTags = QuestionValidator.normalizeSkillTags(request.getSkillTags());
        QuestionValidator.validateEvaluationPoints(request.getEvaluationPoints());
        QuestionValidator.validateOptionalText(request.getKeyPoints(), request.getReferenceAnswer());
        List<EvaluationPointDTO> evaluationPoints = request.getEvaluationPoints();
        if (evaluationPoints != null && evaluationPoints.isEmpty()) {
            evaluationPoints = null;
        }

        JobQuestion q = new JobQuestion();
        q.setId(id);
        q.setCompanyId(companyId);
        q.setVersion(request.getVersion());
        q.setJobType(request.getJobType().trim());
        q.setQuestionType(request.getQuestionType());
        q.setDifficulty(difficulty);
        q.setContent(content);
        q.setContentSha256(sha256(content));
        q.setSkillTags(toJsonOrNull(skillTags));
        q.setKeyPoints(request.getKeyPoints());
        q.setReferenceAnswer(request.getReferenceAnswer());
        q.setEvaluationPoints(toJsonOrNull(evaluationPoints));
        // REJECTED 编辑回 PENDING_REVIEW（审核三字段由 update SQL 在 status=PENDING_REVIEW 时清空）；否则保持原状态
        if (QuestionStatusEnum.REJECTED.getCode().equals(existing.getStatus())) {
            q.setStatus(QuestionStatusEnum.PENDING_REVIEW.getCode());
        } else {
            q.setStatus(existing.getStatus());
        }

        try {
            // content 变更才查重（未删除行，排除自身）
            if (!q.getContentSha256().equals(existing.getContentSha256())) {
                int active = jobQuestionMapper.countActiveByCompanyAndContentHash(companyId, q.getContentSha256(), id);
                if (active > 0) {
                    throw new BusinessException(JobErrorCode.QUESTION_DUPLICATE_CONTENT);
                }
            }
            int rows = jobQuestionMapper.updateByIdAndCompanyIdAndVersion(q);
            if (rows == 0) {
                throw new BusinessException(JobErrorCode.QUESTION_VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(JobErrorCode.QUESTION_DUPLICATE_CONTENT);
        }
        return toDetailVO(jobQuestionMapper.selectByIdAndCompanyId(id, companyId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id, Integer version) {
        Long companyId = requireCompanyId();
        if (jobQuestionMapper.selectByIdAndCompanyId(id, companyId) == null) {
            throw new BusinessException(JobErrorCode.QUESTION_NOT_FOUND);
        }
        int rows = jobQuestionMapper.softDelete(id, companyId, version);
        if (rows == 0) {
            throw new BusinessException(JobErrorCode.QUESTION_VERSION_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrQuestionDetailVO changeQuestionStatus(Long id, QuestionStatusRequest request) {
        Long companyId = requireCompanyId();
        JobQuestion existing = jobQuestionMapper.selectByIdAndCompanyId(id, companyId);
        if (existing == null) {
            throw new BusinessException(JobErrorCode.QUESTION_NOT_FOUND);
        }
        int rows;
        if ("ENABLE".equals(request.getAction())) {
            if (!QuestionStatusEnum.INACTIVE.getCode().equals(existing.getStatus())) {
                throw new BusinessException(JobErrorCode.QUESTION_ILLEGAL_STATE);
            }
            rows = jobQuestionMapper.enable(id, companyId, request.getVersion());
        } else if ("DISABLE".equals(request.getAction())) {
            if (!QuestionStatusEnum.ACTIVE.getCode().equals(existing.getStatus())) {
                throw new BusinessException(JobErrorCode.QUESTION_ILLEGAL_STATE);
            }
            rows = jobQuestionMapper.disable(id, companyId, request.getVersion());
        } else {
            throw new BusinessException(400, "操作类型不合法");
        }
        if (rows == 0) {
            throw new BusinessException(JobErrorCode.QUESTION_VERSION_CONFLICT);
        }
        return toDetailVO(jobQuestionMapper.selectByIdAndCompanyId(id, companyId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrQuestionDetailVO reviewQuestion(Long id, QuestionReviewRequest request) {
        Long companyId = requireCompanyId();
        Long userId = UserContext.getUserId();
        JobQuestion existing = jobQuestionMapper.selectByIdAndCompanyId(id, companyId);
        if (existing == null) {
            throw new BusinessException(JobErrorCode.QUESTION_NOT_FOUND);
        }
        if (!QuestionStatusEnum.PENDING_REVIEW.getCode().equals(existing.getStatus())) {
            throw new BusinessException(JobErrorCode.QUESTION_REVIEW_INVALID);
        }
        String status;
        String reason = null;
        if ("APPROVE".equals(request.getAction())) {
            status = QuestionStatusEnum.ACTIVE.getCode();
        } else if ("REJECT".equals(request.getAction())) {
            status = QuestionStatusEnum.REJECTED.getCode();
            reason = request.getReason();
            if (!StringUtils.hasText(reason)) {
                throw new BusinessException(JobErrorCode.QUESTION_REVIEW_INVALID);
            }
        } else {
            throw new BusinessException(JobErrorCode.QUESTION_REVIEW_INVALID);
        }
        int rows = jobQuestionMapper.review(id, companyId, request.getVersion(), status,
                userId, LocalDateTime.now(), reason);
        if (rows == 0) {
            throw new BusinessException(JobErrorCode.QUESTION_VERSION_CONFLICT);
        }
        return toDetailVO(jobQuestionMapper.selectByIdAndCompanyId(id, companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Long countPending() {
        return jobQuestionMapper.countPending(requireCompanyId());
    }

    // ==================== 私有辅助 ====================

    /** 企业上下文（companyId 从 UserContext 取，不信任前端） */
    private Long requireCompanyId() {
        Long companyId = UserContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException(403, "缺少企业上下文");
        }
        return companyId;
    }

    /** 分页边界校验：page 1~100、size 1~50 */
    private void validatePage(int page, int size) {
        if (page < 1 || page > 100) {
            throw new BusinessException(400, "页码需在1~100之间");
        }
        if (size < 1 || size > 50) {
            throw new BusinessException(400, "每页条数需在1~50之间");
        }
    }

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

    /** skill_tags JSON → List<String>；失败/空 → 空列表 */
    private List<String> parseSkillTags(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> tags = JsonUtil.getMapper().readValue(json, new TypeReference<List<String>>() {});
            return tags == null ? Collections.emptyList() : tags;
        } catch (Exception e) {
            log.warn("解析 skill_tags 失败，返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** evaluation_points JSON → List<EvaluationPointDTO>；失败/空 → 空列表 */
    private List<EvaluationPointDTO> parseEvaluationPoints(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<EvaluationPointDTO> points = JsonUtil.getMapper().readValue(json,
                    new TypeReference<List<EvaluationPointDTO>>() {});
            return points == null ? Collections.emptyList() : points;
        } catch (Exception e) {
            log.warn("解析 evaluation_points 失败，返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 列表 VO（不含 referenceAnswer/keyPoints/evaluationPoints） */
    private HrQuestionListVO toListVO(JobQuestion q) {
        HrQuestionListVO vo = new HrQuestionListVO();
        vo.setId(q.getId());
        vo.setJobType(q.getJobType());
        vo.setQuestionType(q.getQuestionType());
        vo.setDifficulty(q.getDifficulty());
        vo.setContent(q.getContent());
        vo.setSkillTags(parseSkillTags(q.getSkillTags()));
        vo.setSource(q.getSource());
        vo.setStatus(q.getStatus());
        vo.setVersion(q.getVersion());
        vo.setCreatedAt(q.getCreatedAt());
        return vo;
    }

    /** 详情 VO（完整字段含审核信息） */
    private HrQuestionDetailVO toDetailVO(JobQuestion q) {
        HrQuestionDetailVO vo = new HrQuestionDetailVO();
        vo.setId(q.getId());
        vo.setJobType(q.getJobType());
        vo.setSkillTags(parseSkillTags(q.getSkillTags()));
        vo.setQuestionType(q.getQuestionType());
        vo.setDifficulty(q.getDifficulty());
        vo.setContent(q.getContent());
        vo.setKeyPoints(q.getKeyPoints());
        vo.setReferenceAnswer(q.getReferenceAnswer());
        vo.setEvaluationPoints(parseEvaluationPoints(q.getEvaluationPoints()));
        vo.setSource(q.getSource());
        vo.setStatus(q.getStatus());
        vo.setVersion(q.getVersion());
        vo.setCreatedBy(q.getCreatedBy());
        vo.setCreatedAt(q.getCreatedAt());
        vo.setUpdatedAt(q.getUpdatedAt());
        vo.setReviewedBy(q.getReviewedBy());
        vo.setReviewedAt(q.getReviewedAt());
        vo.setReviewReason(q.getReviewReason());
        return vo;
    }
}

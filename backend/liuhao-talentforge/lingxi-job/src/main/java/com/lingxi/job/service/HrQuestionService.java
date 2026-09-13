package com.lingxi.job.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.job.domain.dto.query.QuestionListQuery;
import com.lingxi.job.domain.dto.request.QuestionCreateRequest;
import com.lingxi.job.domain.dto.request.QuestionReviewRequest;
import com.lingxi.job.domain.dto.request.QuestionStatusRequest;
import com.lingxi.job.domain.dto.request.QuestionUpdateRequest;
import com.lingxi.job.domain.vo.HrQuestionDetailVO;
import com.lingxi.job.domain.vo.HrQuestionListVO;

/**
 * 企业私有题库管理服务（B端 HR，阶段6.1）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
public interface HrQuestionService {

    /**
     * 题库分页列表（本企业，四筛 jobType/questionType/difficulty/status，列表脱敏）
     */
    PageResult<HrQuestionListVO> listQuestions(QuestionListQuery query);

    /**
     * 题库详情（完整字段含审核信息，企业隔离）
     */
    HrQuestionDetailVO getQuestionDetail(Long id);

    /**
     * 新增题目（source=HR_CREATED、status=ACTIVE；content_sha256 企业内去重，软删行复用恢复）
     */
    HrQuestionDetailVO createQuestion(QuestionCreateRequest request);

    /**
     * 编辑题目（乐观锁；REJECTED 编辑回 PENDING_REVIEW 并清 review）
     */
    HrQuestionDetailVO updateQuestion(Long id, QuestionUpdateRequest request);

    /**
     * 软删除题目（乐观锁）
     */
    void deleteQuestion(Long id, Integer version);

    /**
     * 启用/停用（ACTIVE↔INACTIVE，乐观锁）
     */
    HrQuestionDetailVO changeQuestionStatus(Long id, QuestionStatusRequest request);

    /**
     * 审核流转（PENDING_REVIEW→ACTIVE/REJECTED，落 reviewed 三字段）
     */
    HrQuestionDetailVO reviewQuestion(Long id, QuestionReviewRequest request);

    /**
     * 待审核计数（本企业）
     */
    Long countPending();
}

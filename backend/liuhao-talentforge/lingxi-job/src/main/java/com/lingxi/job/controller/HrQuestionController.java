package com.lingxi.job.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.job.domain.dto.query.QuestionListQuery;
import com.lingxi.job.domain.dto.request.QuestionCreateRequest;
import com.lingxi.job.domain.dto.request.QuestionReviewRequest;
import com.lingxi.job.domain.dto.request.QuestionStatusRequest;
import com.lingxi.job.domain.dto.request.QuestionUpdateRequest;
import com.lingxi.job.domain.vo.HrQuestionDetailVO;
import com.lingxi.job.domain.vo.HrQuestionListVO;
import com.lingxi.job.service.HrQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * B端 HR 企业私有题库管理接口（阶段6.1）
 * <p>类级 @RequireLogin 兜底未登录，方法级 @RequireRole("HR") 限定企业 HR 角色。
 * companyId 一律从 UserContext 获取，不信任前端传入。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@RestController
@RequestMapping("/api/v1/hr/questions")
@RequiredArgsConstructor
@Validated
@RequireLogin
public class HrQuestionController {

    private final HrQuestionService hrQuestionService;

    /**
     * 题库分页列表（本企业，四筛 jobType/questionType/difficulty/status，列表脱敏）
     */
    @GetMapping
    @RequireRole("HR")
    public Result<PageResult<HrQuestionListVO>> listQuestions(QuestionListQuery query) {
        return Result.success(hrQuestionService.listQuestions(query));
    }

    /**
     * 题库详情（完整字段含审核信息，企业隔离）
     */
    @GetMapping("/{id:[0-9]+}")
    @RequireRole("HR")
    public Result<HrQuestionDetailVO> getQuestionDetail(@PathVariable Long id) {
        return Result.success(hrQuestionService.getQuestionDetail(id));
    }

    /**
     * 新增题目（source=HR_CREATED、status=ACTIVE，content 企业内去重）
     */
    @PostMapping
    @RequireRole("HR")
    public Result<HrQuestionDetailVO> createQuestion(@RequestBody @Valid QuestionCreateRequest request) {
        return Result.success("题目创建成功", hrQuestionService.createQuestion(request));
    }

    /**
     * 编辑题目（乐观锁；REJECTED 编辑回 PENDING_REVIEW）
     */
    @PutMapping("/{id:[0-9]+}")
    @RequireRole("HR")
    public Result<HrQuestionDetailVO> updateQuestion(@PathVariable Long id,
                                                     @RequestBody @Valid QuestionUpdateRequest request) {
        return Result.success("题目编辑成功", hrQuestionService.updateQuestion(id, request));
    }

    /**
     * 软删除题目（HTTP 200 + Result&lt;Void&gt; code=200，对齐岗位删除惯例）
     * <p>version 必填（@RequestParam required=true），与其他写操作（编辑/启停/审核）的乐观锁契约一致；
     * 漏传时由 GlobalExceptionHandler.handleMissingParamException 统一返回 HTTP 400 + code=400，
     * message 为「缺少请求参数: version」。</p>
     */
    @DeleteMapping("/{id:[0-9]+}")
    @RequireRole("HR")
    public Result<Void> deleteQuestion(@PathVariable Long id,
                                       @RequestParam Integer version) {
        hrQuestionService.deleteQuestion(id, version);
        return Result.success("题目删除成功", null);
    }

    /**
     * 启用/停用（ACTIVE↔INACTIVE）
     */
    @PatchMapping("/{id:[0-9]+}/status")
    @RequireRole("HR")
    public Result<HrQuestionDetailVO> changeQuestionStatus(@PathVariable Long id,
                                                           @RequestBody @Valid QuestionStatusRequest request) {
        return Result.success("状态变更成功", hrQuestionService.changeQuestionStatus(id, request));
    }

    /**
     * 审核流转（PENDING_REVIEW→ACTIVE/REJECTED，阶段6.1 预置接口，6.3 AI 导入题起有真实来源）
     */
    @PostMapping("/{id:[0-9]+}/review")
    @RequireRole("HR")
    public Result<HrQuestionDetailVO> reviewQuestion(@PathVariable Long id,
                                                     @RequestBody @Valid QuestionReviewRequest request) {
        return Result.success("审核成功", hrQuestionService.reviewQuestion(id, request));
    }

    /**
     * 待审核计数（审核入口）
     */
    @GetMapping("/pending-count")
    @RequireRole("HR")
    public Result<Long> countPending() {
        return Result.success(hrQuestionService.countPending());
    }
}

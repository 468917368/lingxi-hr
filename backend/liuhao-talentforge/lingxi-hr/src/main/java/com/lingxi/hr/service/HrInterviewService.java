package com.lingxi.hr.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.hr.domain.dto.EvaluationDTO;
import com.lingxi.hr.domain.dto.InterviewCreateDTO;
import com.lingxi.hr.domain.vo.EvaluationDetailVO;
import com.lingxi.hr.domain.vo.EvaluationResultVO;
import com.lingxi.hr.domain.vo.InterviewVO;
import com.lingxi.hr.domain.vo.PendingEvaluationVO;

import java.util.List;

/**
 * 面试协同服务（系分文档 5.5.3，Day 4-5）
 *
 * @author 成员D
 * @since 2026-08-06
 */
public interface HrInterviewService {

    /**
     * 创建面试安排：投递 SCREENED→INTERVIEWING + 双端通知
     */
    InterviewVO createInterview(Long companyId, InterviewCreateDTO dto);

    /**
     * 面试列表（条件分页，interviewerId 供面试官端「我的面试」；method 面试方式筛选；keyword 候选人姓名搜索）
     */
    PageResult<InterviewVO> listInterviews(Long companyId, String status, String dateRange, Long jobId,
                                           Long interviewerId, String method, String keyword,
                                           Integer page, Integer size);

    /**
     * 开始面试：PENDING/SCHEDULED → IN_PROGRESS
     */
    void startInterview(Long companyId, Long interviewId);

    /**
     * 取消面试：非终态 → CANCELLED + 投递回退 SCREENED
     */
    void cancelInterview(Long companyId, Long interviewId);

    /**
     * 待评估列表：IN_PROGRESS 且无正式评估
     */
    List<PendingEvaluationVO> pendingEvaluations(Long companyId);

    /**
     * 查看面试评估（含草稿）
     */
    EvaluationDetailVO getEvaluation(Long companyId, Long interviewId);

    /**
     * 录入面试评估：草稿可覆盖转正式；正式提交触发面试 COMPLETED + 投递联动
     */
    EvaluationResultVO submitEvaluation(Long companyId, Long interviewId, EvaluationDTO dto);
}

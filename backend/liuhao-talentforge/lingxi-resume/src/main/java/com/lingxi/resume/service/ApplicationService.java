package com.lingxi.resume.service;

import com.lingxi.common.domain.PageRequest;
import com.lingxi.common.domain.PageResult;
import com.lingxi.resume.domain.dto.ApplyRequestDTO;
import com.lingxi.resume.domain.vo.ApplicationDetailVO;
import com.lingxi.resume.domain.vo.ApplicationTrendVO;
import com.lingxi.resume.domain.vo.ApplicationVO;
import com.lingxi.resume.domain.vo.InternalApplicationVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 投递服务
 *
 * <p>覆盖投递闭环 P0 主线：一键投递 → 状态机流转 → 日志审计 → MQ 事件。
 * C 端接口按候选人隔离；HR 端内部接口按企业隔离。
 *
 * @author 成员C
 * @since 2026-08-04
 */
public interface ApplicationService {

    /**
     * 一键投递（C端）
     *
     * <p>校验链：简历归属/解析完成（或默认简历）→ 岗位在招 → 防重复投递 → 入库 SUBMITTED
     * → 状态日志 → 事务提交后发 NEW_APPLICATION 事件。
     *
     * @param dto 投递请求（jobId 必传，resumeId 可选为空则用默认简历）
     * @return 投递结果（含岗位/企业信息）
     */
    ApplicationVO submit(ApplyRequestDTO dto);

    /**
     * 投递列表（C端分页，candidateId 隔离，JOIN job_post/hr_company）
     */
    PageResult<ApplicationVO> listApplications(PageRequest pageRequest);

    /**
     * 投递详情（C端，含状态时间线，仅本人可查）
     */
    ApplicationDetailVO getApplicationDetail(Long id);

    /**
     * 撤回投递（C端，仅 SUBMITTED/VIEWED/SCREENED 可撤回，面试安排后不可撤回；
     * 事务提交后发 APPLICATION_WITHDRAWN 事件）
     */
    void withdraw(Long id);

    /**
     * 接受Offer（C端，仅 OFFERED 待录用状态，终态 OFFER_ACCEPTED）
     */
    void acceptOffer(Long id);

    /**
     * 拒绝Offer（C端，仅 OFFERED 待录用状态，终态 OFFER_DECLINED；
     * 事务内同步 lingxi-hr：hr_offer SENT→REJECTED，失败回滚本地）
     *
     * @param id           投递记录ID
     * @param rejectReason 拒绝原因（可选，透传至 lingxi-hr）
     */
    void declineOffer(Long id, String rejectReason);

    /**
     * HR端候选人列表（按 companyId 隔离，分页，可选筛选/搜索/排序）
     *
     * @param companyId      企业ID（必传）
     * @param jobId          岗位ID（可选）
     * @param status         投递状态（可选）
     * @param minMatchScore  最低匹配分（可选）
     * @param keyword        候选人姓名模糊搜索（可选，仅搜姓名）
     * @param sortBy         排序字段（可选，仅 "matchScore"/"submittedAt"；不在白名单退化为默认）
     * @param page           页码（可选，null 或 &lt;=0 时不启用分页）
     * @param pageSize       每页条数（可选，与 page 同时传才生效）
     * @return 分页候选人列表（含 total/page/pageSize）
     */
    PageResult<InternalApplicationVO> listByCompany(Long companyId, List<Long> applicationIds, Long jobId, String status,
                                                    BigDecimal minMatchScore, String keyword, String sortBy,
                                                    Integer page, Integer pageSize);

    /**
     * HR端候选人详情（对齐 HR ResumeFeignClient 契约字段，含 companyId）
     */
    InternalApplicationVO getInternalById(Long id);

    /**
     * HR端状态更新（唯一入口：HR 操作投递状态的通道）
     *
     * @param id             投递记录ID
     * @param status         目标状态（VIEWED/SCREENED/REJECTED/OFFERABLE/OFFERED；OFFER_ACCEPTED/OFFER_DECLINED 仅候选人端操作）
     * @param operatorId     操作人HR用户ID
     * @param rejectFeedback 落选反馈JSON（可选，仅 REJECTED 时传入；格式 {"reason":"...","suggestions":["..."]}）
     */
    void updateStatusByHr(Long id, String status, Long operatorId, String rejectFeedback);

    /**
     * 消费面试评估事件（SYSTEM 角色流转，interview-event INTERVIEW_EVALUATED）
     *
     * <p>PASS → OFFERABLE（面试通过）；FAIL → REJECTED（面试未通过）。
     * 当前状态不满足白名单（如非 INTERVIEWING）或状态冲突时仅告警，不抛出。
     *
     * @param applicationId 投递记录ID
     * @param result        面试结果：PASS/FAIL
     */
    void handleInterviewEvaluated(Long applicationId, String result);

    /**
     * 平台投递总数（管理后台看板）
     */
    Long getApplicationCount();

    /**
     * 近 N 天投递趋势（管理后台看板，含今日投递数）
     *
     * @param days 天数（默认7）
     */
    ApplicationTrendVO getApplicationTrend(int days);

    /**
     * 更新 AI 分析结果（供 lingxi-user 异步分析后调用）
     *
     * @param id   投递记录ID
     * @param body {aiScore, aiAnalysis}
     */
    void updateAiAnalysis(Long id, Map<String, Object> body);
}

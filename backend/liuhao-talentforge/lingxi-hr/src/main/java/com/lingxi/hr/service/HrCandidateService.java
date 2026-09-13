package com.lingxi.hr.service;

import com.lingxi.common.domain.PageResult;
import com.lingxi.hr.domain.dto.MarkCandidateDTO;
import com.lingxi.hr.domain.vo.CandidateVO;
import com.lingxi.hr.domain.vo.MarkCandidateResultVO;
import com.lingxi.hr.domain.vo.TopCandidateVO;
import com.lingxi.hr.feign.dto.ResumeDetailDTO;

import java.util.List;

/**
 * 候选人管理服务（系分文档 5.5.2，Day 3）
 *
 * @author 成员D
 * @since 2026-08-05
 */
public interface HrCandidateService {

    /**
     * 候选人列表（筛选/排序/分页，跨服务拼装）
     *
     * @param interviewerId 面试官过滤：INTERVIEWER 必须传本人 id，列表范围限定本人负责的投递；HR_ADMIN 可空
     */
    PageResult<CandidateVO> listCandidates(Long companyId, Long interviewerId, Long jobId, String status,
                                           Integer minMatchScore, String keyword, String sortBy,
                                           Integer page, Integer size);

    /**
     * Top5 高潜推荐（本企业所有岗位或指定岗位，不足 5 人返回实际数量）
     *
     * @param interviewerId 面试官过滤：INTERVIEWER 必须传本人 id；HR_ADMIN 可空
     */
    List<TopCandidateVO> topCandidates(Long companyId, Long interviewerId, Long jobId);

    /**
     * 标记候选人合适/不合适（筛选决策，仅 HR_ADMIN）
     */
    MarkCandidateResultVO markCandidate(Long companyId, Long applicationId, MarkCandidateDTO dto);

    /**
     * 查看候选人简历（HR 人才库「查看简历」按钮，需求单 v1.1）
     *
     * <p>流程：校验 ACTIVE 成员 → 复用投递详情校验（4300/4301）拿 resumeId →
     * Feign 调 lingxi-resume 内部接口 {@code GET /internal/resumes/{id}/detail} → 透传简历详情。
     * 简历不存在（resumeId 为空 / C 返回 RESUME_NOT_FOUND）抛 4303。
     */
    ResumeDetailDTO getCandidateResume(Long companyId, Long applicationId);

    /**
     * 根据用户ID查看候选人简历（人才推荐用）
     */
    ResumeDetailDTO getCandidateResumeByUserId(Long companyId, Long userId);
}

package com.lingxi.resume.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.resume.domain.vo.InternalResumeVO;
import com.lingxi.resume.domain.vo.ResumeDetailVO;
import com.lingxi.resume.mapper.ResumeApplicationMapper;
import com.lingxi.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 简历内部接口（供 lingxi-job / lingxi-hr / lingxi-admin 模块 Feign 调用）
 *
 * <p>两个端点，按消费者对敏感字段的不同需求拆分：
 * <pre>
 * GET  /internal/resumes/{id}         → Result&lt;InternalResumeVO&gt;   （精简，给 lingxi-job 面试出题）
 * GET  /internal/resumes/{id}/detail  → Result&lt;ResumeDetailVO&gt;     （全量，给 lingxi-hr HR端简历展示）
 * </pre>
 *
 * <p>{@code /internal/**} 经 Gateway AuthFilter 白名单放行（不校验 Token），
 * Service 层不校验归属（跳过 candidateId 校验）。
 *
 * @author 成员C
 * @since 2026-08-05
 */
@Slf4j
@RestController
@RequestMapping("/internal/resumes")
@RequiredArgsConstructor
public class InternalResumeController {

    private final ResumeService resumeService;
    private final ResumeApplicationMapper applicationMapper;

    /**
     * 简历详情-精简（B-lingxi-job 面试出题，仅需 cardStructure）
     *
     * @param id 简历ID
     * @return 精简视图（id / parseStatus / cardStructure / resumeMdUrl / candidateName），无联系方式
     */
    @GetMapping("/{id}")
    public Result<InternalResumeVO> getResumeDetail(@PathVariable("id") Long id) {
        return Result.success(resumeService.getInternalResumeDetail(id));
    }

    /**
     * 简历详情-全量（D-lingxi-hr HR端简历展示，需完整信息含联系方式）
     *
     * @param id 简历ID
     * @return 全量 ResumeDetailVO（含 phone / email / wechat 等所有字段）
     */
    @GetMapping("/{id}/detail")
    public Result<ResumeDetailVO> getResumeFullDetail(@PathVariable("id") Long id) {
        return Result.success(resumeService.getInternalResumeFullDetail(id));
    }

    /**
     * 按用户ID获取默认简历（供 Agent 工具调用）
     *
     * @param userId 用户ID（候选人ID）
     * @return 精简简历视图，无简历时返回空数据
     */
    @GetMapping("/user/{userId}")
    public Result<InternalResumeVO> getResumeByUserId(@PathVariable("userId") Long userId) {
        InternalResumeVO vo = resumeService.getInternalResumeByUserId(userId);
        return Result.success(vo);
    }

    /**
     * 获取用户已投递的岗位ID列表
     *
     * @param userId 用户ID（候选人ID）
     * @return 已投递的岗位ID列表
     */
    @GetMapping("/user/{userId}/applied-jobs")
    public Result<List<Long>> getAppliedJobIds(@PathVariable("userId") Long userId) {
        List<Long> jobIds = applicationMapper.selectAppliedJobIds(userId);
        return Result.success(jobIds);
    }
}

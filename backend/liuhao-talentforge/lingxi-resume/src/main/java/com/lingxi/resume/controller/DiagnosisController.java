package com.lingxi.resume.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.resume.domain.dto.DiagnosisRequest;
import com.lingxi.resume.domain.vo.DiagnosisDetailVO;
import com.lingxi.resume.domain.vo.DiagnosisHistoryVO;
import com.lingxi.resume.domain.vo.DiagnosisTaskStatusVO;
import com.lingxi.resume.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.List;

/**
 * 简历诊断控制器
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-9 简历诊断。
 *
 * @author 成员C
 * @since 2026-08-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class DiagnosisController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final DiagnosisService diagnosisService;

    /**
     * 发起 AI 诊断（SSE 流式）
     *
     * <p>SSE 事件：thinking → final | error
     */
    @PostMapping(value = "/{id}/diagnosis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter diagnose(@PathVariable("id") Long id,
                               @Valid @RequestBody DiagnosisRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        log.info("诊断请求: resumeId={}, career={}, refresh={}",
                id, request.getCareer(), request.getRefresh());
        diagnosisService.diagnose(id, request.getCareer(),
                Boolean.TRUE.equals(request.getRefresh()), emitter);
        return emitter;
    }

    /**
     * 诊断历史列表
     */
    @GetMapping("/{id}/diagnosis")
    public Result<List<DiagnosisHistoryVO>> listHistory(@PathVariable("id") Long id) {
        return Result.success(diagnosisService.listHistory(id));
    }

    /**
     * 诊断任务状态（方案C异步化：前端轮询，感知后台任务完成）
     *
     * <p>state 取值：RUNNING（进行中）/ COMPLETED（完成，含 reportId）/
     * FAILED（失败）/ NONE（无任务或已过期）
     */
    @GetMapping("/{id}/diagnosis/status")
    public Result<DiagnosisTaskStatusVO> getTaskStatus(@PathVariable("id") Long id,
                                                       @RequestParam String career) {
        return Result.success(diagnosisService.getTaskStatus(id, career.trim()));
    }

    /**
     * 诊断报告详情（含 Markdown 正文）
     */
    @GetMapping("/{id}/diagnosis/{reportId}")
    public Result<DiagnosisDetailVO> getDetail(@PathVariable("id") Long id,
                                               @PathVariable("reportId") Long reportId) {
        return Result.success(diagnosisService.getDetail(reportId));
    }
}

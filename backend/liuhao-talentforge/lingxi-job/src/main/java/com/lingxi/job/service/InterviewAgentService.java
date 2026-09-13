package com.lingxi.job.service;

import com.lingxi.job.domain.dto.request.GenerateQuestionsRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Interview Agent SSE 出题服务（阶段4，F-17）
 *
 * @author 成员B
 * @since 2026-08-04
 */
public interface InterviewAgentService {

    /**
     * 为指定岗位发起 Interview Agent SSE 出题
     *
     * @param jobId   岗位ID（路径）
     * @param request 出题请求（含投递记录ID、难度）
     * @return SSE 发射器（progress* → result → done | error → close）
     */
    SseEmitter generateQuestions(Long jobId, GenerateQuestionsRequest request);
}

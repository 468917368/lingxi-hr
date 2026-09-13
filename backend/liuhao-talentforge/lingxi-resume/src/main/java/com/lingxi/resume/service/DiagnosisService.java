package com.lingxi.resume.service;

import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.domain.vo.DiagnosisDetailVO;
import com.lingxi.resume.domain.vo.DiagnosisHistoryVO;
import com.lingxi.resume.domain.vo.DiagnosisTaskStatusVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 简历诊断服务
 *
 * @author 成员C
 * @since 2026-08-06
 */
public interface DiagnosisService {

    /**
     * 发起 AI 诊断（SSE 流式）
     *
     * @param resumeId 简历ID
     * @param career   目标职业名
     * @param refresh  是否强制重新生成（true 跳过缓存；false 命中缓存——同简历+同职业
     *                 且简历未修改时直接返回历史报告，简历修改后自动失效）
     * @param emitter  SSE 连接
     */
    void diagnose(Long resumeId, String career, boolean refresh, SseEmitter emitter);

    /**
     * 查询诊断历史列表（同简历的所有诊断记录）
     *
     * @param resumeId 简历ID
     * @return 历史记录列表（按时间倒序）
     */
    List<DiagnosisHistoryVO> listHistory(Long resumeId);

    /**
     * 查询诊断报告详情（含 Markdown 正文，从 MinIO 读取）
     *
     * @param reportId 诊断报告ID
     * @return 报告详情
     */
    DiagnosisDetailVO getDetail(Long reportId);

    /**
     * 查询诊断任务状态（方案C异步化：跳页后回页轮询的完成信号）
     *
     * @param resumeId 简历ID
     * @param career   目标职业名
     * @return RUNNING / COMPLETED(reportId) / FAILED / NONE
     */
    DiagnosisTaskStatusVO getTaskStatus(Long resumeId, String career);

    /**
     * 异步诊断执行入口（仅内部自调用，勿外部调用）。
     *
     * <p>方案C：{@code diagnose()} 注册订阅者后经 Spring 代理调用本方法触发
     * {@code @Async("diagnosisExecutor")}——自调用不走 AOP 代理，必须由容器
     * {@code getBean(DiagnosisService.class)} 取接口代理（JDK 动态代理）后调用。
     *
     * @param resume  已校验的简历实体
     * @param career  目标职业（trim 后）
     * @param taskKey 任务 key（resumeId:careerBase64）
     */
    void diagnoseAsync(Resume resume, String career, String taskKey);
}

package com.lingxi.resume.domain.vo;

import lombok.Data;

/**
 * 诊断任务状态 VO（前端轮询用）
 *
 * <p>state 取值：
 * <ul>
 *   <li>RUNNING —— 诊断进行中（跳页后回页轮询感知）</li>
 *   <li>COMPLETED —— 已完成，reportId 指向最新报告</li>
 *   <li>FAILED —— 执行失败</li>
 *   <li>NONE —— 无任务（无历史 key / 已过期 / Redis 异常兜底）</li>
 * </ul>
 *
 * @author 成员C
 * @since 2026-08-08
 */
@Data
public class DiagnosisTaskStatusVO {

    /** RUNNING / COMPLETED / FAILED / NONE */
    private String state;

    /** 完成后的报告 ID（COMPLETED 时非空） */
    private Long reportId;

    /** 目标职业 */
    private String career;
}

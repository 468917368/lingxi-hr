package com.lingxi.resume.domain.vo;

import lombok.Data;

/**
 * 解析任务状态 VO（前端轮询用）
 *
 * <p>state 取值：
 * <ul>
 *   <li>RUNNING —— 解析进行中（跳页后回页轮询感知）</li>
 *   <li>COMPLETED —— 已完成（前端直接刷新列表/拉详情即可）</li>
 *   <li>FAILED —— 执行失败（记录保留，用户手动删除）</li>
 *   <li>NONE —— 无任务（无历史 key / 已过期 / Redis 异常兜底）</li>
 * </ul>
 *
 * @author 成员C
 * @since 2026-08-09
 */
@Data
public class ParseTaskStatusVO {

    /** RUNNING / COMPLETED / FAILED / NONE */
    private String state;
}

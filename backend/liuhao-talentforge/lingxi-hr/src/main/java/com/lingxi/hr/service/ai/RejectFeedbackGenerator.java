package com.lingxi.hr.service.ai;

import com.lingxi.hr.domain.dto.RejectFeedbackDTO;
import com.lingxi.hr.feign.dto.ApplicationDTO;

/**
 * 落选反馈生成器
 *
 * <p>标记候选人"不合适"时生成落选原因 + 2-3 条提升建议。
 * 本期提供模板降级实现 {@code TemplateRejectFeedbackGenerator}，
 * Day 6-7 已具备百宝箱 LLM 链路，后续可替换为真实 LLM 实现。
 *
 * @author 成员D
 * @since 2026-08-05
 */
public interface RejectFeedbackGenerator {

    /**
     * 生成落选反馈
     *
     * @param application 投递记录（含匹配度等快照字段）
     * @return 落选原因 + 提升建议
     */
    RejectFeedbackDTO generate(ApplicationDTO application);
}

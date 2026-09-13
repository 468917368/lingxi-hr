package com.lingxi.hr.service.ai.impl;

import com.lingxi.hr.domain.dto.RejectFeedbackDTO;
import com.lingxi.hr.feign.dto.ApplicationDTO;
import com.lingxi.hr.service.ai.RejectFeedbackGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板降级落选反馈生成器
 *
 * <p>按投递匹配度分档生成原因 + 2-3 条建议。非个性化内容，仅保证接口可用；
 * TODO：待接入 DeepSeek/百宝箱真实 LLM 生成个性化落选反馈（复用 lingxi-hr agent 链路）。
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Slf4j
@Component
public class TemplateRejectFeedbackGenerator implements RejectFeedbackGenerator {

    @Override
    public RejectFeedbackDTO generate(ApplicationDTO application) {
        BigDecimal matchScore = application.getMatchScore() != null
                ? application.getMatchScore() : BigDecimal.ZERO;

        RejectFeedbackDTO dto = new RejectFeedbackDTO();
        List<String> suggestions = new ArrayList<>(3);

        if (matchScore.compareTo(BigDecimal.valueOf(60)) < 0) {
            dto.setReason("您的综合能力与岗位要求存在一定差距，本次未能通过简历筛选。");
            suggestions.add("针对目标岗位的技术栈补充项目实战经验");
            suggestions.add("优化简历中与岗位要求匹配的技能关键词描述");
            suggestions.add("关注平台其他与您背景更匹配的岗位机会");
        } else if (matchScore.compareTo(BigDecimal.valueOf(80)) < 0) {
            dto.setReason("您的简历与本岗位匹配度未达筛选标准，感谢您的投递。");
            suggestions.add("突出与目标岗位相关的项目成果并量化数据");
            suggestions.add("补充岗位要求方向的项目经历或学习成果");
        } else {
            dto.setReason("综合评估后，本次招聘暂不考虑您的投递，感谢您的关注。");
            suggestions.add("保持简历更新，后续如有合适岗位系统将为您推荐");
        }

        dto.setSuggestions(suggestions);
        log.info("模板生成落选反馈: applicationId={}, matchScore={}", application.getId(), matchScore);
        return dto;
    }
}

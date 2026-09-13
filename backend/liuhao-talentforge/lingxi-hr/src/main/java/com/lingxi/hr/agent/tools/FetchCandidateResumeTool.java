package com.lingxi.hr.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.hr.agent.feign.AbilityModelDTO;
import com.lingxi.hr.agent.feign.ResumeApiFeignClient;
import com.lingxi.hr.agent.feign.ResumeDetailDTO;
import com.lingxi.hr.agent.feign.ResumeListItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 候选人简历获取 Tool（Feign C 用户接口，token 透传以求职者身份）
 * <p>简历输入 = 能力模型为主（5 维分 + subDimensions）+ 简历详情为辅（cardStructure 要点）。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchCandidateResumeTool {

    private final ResumeApiFeignClient resumeApiFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * 获取简历输入文本
     *
     * @param resumeId 简历ID（可为 null，缺省取默认简历）
     * @return 简历输入（text + used 标记）
     */
    public ResumeInput fetch(Long resumeId) {
        Long targetResumeId = resolveResumeId(resumeId);
        if (targetResumeId == null) {
            log.warn("未获取到候选人简历，按通用岗位要求出题");
            return ResumeInput.empty();
        }

        AbilityModelDTO ability = fetchAbilityModel(targetResumeId);
        ResumeDetailDTO detail = fetchResumeDetail(targetResumeId);
        if (ability == null && detail == null) {
            return ResumeInput.empty();
        }

        StringBuilder sb = new StringBuilder();
        if (ability != null) {
            sb.append("能力模型：\n")
              .append("专业技能:").append(ability.getProfessionalSkillScore())
              .append(" / 项目经验:").append(ability.getWorkExperienceScore())
              .append(" / 行业认知:").append(ability.getIndustryKnowledgeScore())
              .append(" / 综合素质:").append(ability.getComprehensiveQualityScore())
              .append(" / 学习成长:").append(ability.getLearningGrowthScore());
            if (ability.getSubDimensions() != null) {
                String sub = toJson(ability.getSubDimensions());
                if (!sub.isEmpty()) {
                    sb.append("\n子维度：").append(truncate(sub, 800));
                }
            }
        }
        if (detail != null && detail.getCardStructure() != null) {
            String card = toJson(detail.getCardStructure());
            if (!card.isEmpty()) {
                sb.append("\n简历要点：").append(truncate(card, 1000));
            }
        }
        return ResumeInput.of(sb.toString(), true);
    }

    // ==================== 私有方法 ====================

    private Long resolveResumeId(Long resumeId) {
        if (resumeId != null) {
            return resumeId;
        }
        try {
            Result<PageResult<ResumeListItemDTO>> result = resumeApiFeignClient.listResumes(1, 20);
            if (result != null && result.isSuccess()
                    && result.getData() != null && result.getData().getList() != null
                    && !result.getData().getList().isEmpty()) {
                for (ResumeListItemDTO item : result.getData().getList()) {
                    if (Integer.valueOf(1).equals(item.getIsDefault())) {
                        return item.getId();
                    }
                }
                return result.getData().getList().get(0).getId();
            }
        } catch (Exception e) {
            log.warn("获取简历列表失败", e);
        }
        return null;
    }

    private AbilityModelDTO fetchAbilityModel(Long resumeId) {
        try {
            Result<AbilityModelDTO> result = resumeApiFeignClient.getAbilityModel(resumeId);
            if (result != null && result.isSuccess()) {
                return result.getData();
            }
            log.warn("能力模型返回为空: resumeId={}", resumeId);
        } catch (Exception e) {
            log.warn("获取能力模型失败: resumeId={}", resumeId, e);
        }
        return null;
    }

    private ResumeDetailDTO fetchResumeDetail(Long resumeId) {
        try {
            Result<ResumeDetailDTO> result = resumeApiFeignClient.getResume(resumeId);
            if (result != null && result.isSuccess()) {
                return result.getData();
            }
            log.warn("简历详情返回为空: resumeId={}", resumeId);
        } catch (Exception e) {
            log.warn("获取简历详情失败: resumeId={}", resumeId, e);
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("对象序列化失败", e);
            return String.valueOf(obj);
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    // ==================== 内部类 ====================

    /**
     * 简历输入结果
     */
    @RequiredArgsConstructor
    public static class ResumeInput {
        private final String text;
        private final boolean used;

        public static ResumeInput empty() {
            return new ResumeInput("", false);
        }

        public static ResumeInput of(String text, boolean used) {
            return new ResumeInput(text, used);
        }

        public String getText() {
            return text;
        }

        public boolean isUsed() {
            return used;
        }
    }
}

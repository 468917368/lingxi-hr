package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 岗位画像查询响应（成员 A/D 依赖）
 * <p>不含 hiddenRequirements 和题库数据</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobRequirementResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long jobId;
    private String jobType;
    private List<CoreSkill> coreSkills;
    private List<SoftSkill> softSkills;
    private String industryExperience;
    private List<String> interviewFocus;
    private Boolean profileConfirmed;

    /**
     * 核心技能项
     */
    @Data
    public static class CoreSkill implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String level;
        private Boolean required;
    }

    /**
     * 软能力项
     */
    @Data
    public static class SoftSkill implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String importance;
    }
}

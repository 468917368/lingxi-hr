package com.lingxi.hr.agent.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 岗位考察要点 DTO（来自 lingxi-job /internal/jobs/{jobId}/requirements）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobRequirementDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long jobId;

    /** 通用岗位类型 */
    private String jobType;

    /** 核心技能 */
    private List<CoreSkill> coreSkills;

    /** 软能力 */
    private List<SoftSkill> softSkills;

    /** 行业经验要求 */
    private String industryExperience;

    /** 面试考察重点 */
    private List<String> interviewFocus;

    private Boolean profileConfirmed;

    @Data
    public static class CoreSkill implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String level;
        private Boolean required;
    }

    @Data
    public static class SoftSkill implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String importance;
    }
}

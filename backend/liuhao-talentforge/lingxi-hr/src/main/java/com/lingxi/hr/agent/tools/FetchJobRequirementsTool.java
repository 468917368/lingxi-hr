package com.lingxi.hr.agent.tools;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.agent.feign.JobRequirementDTO;
import com.lingxi.hr.feign.JobFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 岗位考察要点获取 Tool（Feign B /internal/jobs/{jobId}/requirements）
 * <p>失败降级为仅岗位名称，不阻塞出题。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchJobRequirementsTool {

    private final JobFeignClient jobFeignClient;

    /**
     * 获取岗位考察要点文本
     *
     * @param jobId    岗位ID（可为 null）
     * @param jobTitle 岗位名称（兜底/降级用）
     * @return 岗位输入文本（供 LLM 出题）
     */
    public String fetch(Long jobId, String jobTitle) {
        String fallback = "岗位类型：" + (jobTitle == null || jobTitle.isEmpty() ? "未知岗位" : jobTitle);
        if (jobId == null) {
            return fallback;
        }
        try {
            Result<JobRequirementDTO> result = jobFeignClient.getJobRequirements(jobId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return buildJobInfo(result.getData());
            }
            log.warn("岗位考察要点返回为空: jobId={}", jobId);
        } catch (Exception e) {
            log.warn("获取岗位考察要点失败: jobId={}", jobId, e);
        }
        return fallback;
    }

    private String buildJobInfo(JobRequirementDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位类型：").append(dto.getJobType());
        if (dto.getCoreSkills() != null && !dto.getCoreSkills().isEmpty()) {
            sb.append("\n核心技能：");
            sb.append(dto.getCoreSkills().stream()
                    .map(c -> c.getName()
                            + (c.getLevel() != null ? "(" + c.getLevel() + ")" : "")
                            + (Boolean.TRUE.equals(c.getRequired()) ? "(必选)" : ""))
                    .collect(Collectors.joining("、")));
        }
        if (dto.getInterviewFocus() != null && !dto.getInterviewFocus().isEmpty()) {
            sb.append("\n考察重点：").append(String.join("、", dto.getInterviewFocus()));
        }
        if (dto.getSoftSkills() != null && !dto.getSoftSkills().isEmpty()) {
            sb.append("\n软能力：");
            sb.append(dto.getSoftSkills().stream()
                    .map(s -> s.getName() + (s.getImportance() != null ? "(" + s.getImportance() + ")" : ""))
                    .collect(Collectors.joining("、")));
        }
        if (dto.getIndustryExperience() != null && !dto.getIndustryExperience().isEmpty()) {
            sb.append("\n行业经验：").append(dto.getIndustryExperience());
        }
        return sb.toString();
    }
}

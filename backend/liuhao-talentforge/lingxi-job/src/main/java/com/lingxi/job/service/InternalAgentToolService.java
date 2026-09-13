package com.lingxi.job.service;

import com.lingxi.job.domain.vo.QuestionPromptVO;

import java.util.List;
import java.util.Map;

/**
 * 百宝箱 Agent Tool 服务（阶段4）
 * <p>
 * 承载 3 个 Tool 的业务逻辑（runToken 校验、画像/亮点/题库组装），
 * Controller 只保留请求映射。数据访问一律以 runToken 上下文 companyId 隔离。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
public interface InternalAgentToolService {

    /**
     * Tool1：岗位画像（jobId 须与 runToken 绑定一致）
     *
     * @param runToken 运行令牌
     * @param jobId    岗位ID
     * @return 画像数据（jobType/coreSkills/softSkills/interviewFocus）
     */
    Map<String, Object> getJobRequirements(String runToken, Long jobId);

    /**
     * Tool2：简历亮点（返回 runToken 上下文缓存的脱敏亮点，appId 绑定校验）
     *
     * @param runToken 运行令牌
     * @param appId    投递记录ID
     * @return 脱敏简历亮点
     */
    List<String> getHighlights(String runToken, Long appId);

    /**
     * Tool3：题库搜索（companyId/jobType 从 runToken 读取，不从 Query 传）
     *
     * @param runToken     运行令牌
     * @param skillTags    技能标签（逗号分隔）
     * @param questionType 题目类型
     * @param difficulty   难度
     * @return 题库投影（每题型至多 1 条、合计 ≤4 条，不含 referenceAnswer/id/companyId）
     */
    List<QuestionPromptVO> searchQuestions(String runToken, String skillTags,
                                           String questionType, String difficulty);
}

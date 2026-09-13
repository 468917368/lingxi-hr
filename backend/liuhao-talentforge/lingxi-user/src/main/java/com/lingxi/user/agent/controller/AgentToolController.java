package com.lingxi.user.agent.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.user.agent.tools.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent 工具接口（供百宝箱工作流调用）
 * <p>
 * 这些接口是百宝箱Agent的工作流回调端点，供Agent在对话过程中调用。
 * 注意：这些接口在Gateway白名单中（/api/v1/agent/tools/**），不需要登录Token，
 * 但需要传userId参数来标识操作哪个用户的数据。
 * </p>
 * <p>
 * 工具列表：
 * - search: 搜索岗位
 * - detail: 获取岗位详情
 * - resume: 获取用户简历
 * - match: 计算匹配度
 * - company: 查询企业岗位
 * - recommend: 智能推荐
 * - match-score: 计算匹配度（返回纯分数，供投递时调用）
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final SearchJobsTool searchJobsTool;
    private final GetJobDetailTool getJobDetailTool;
    private final GetMyResumeTool getMyResumeTool;
    private final CalculateMatchTool calculateMatchTool;
    private final GetCompanyJobsTool getCompanyJobsTool;

    /**
     * 搜索岗位（百宝箱工具回调）
     * <p>
     * 按关键词和城市搜索岗位列表，返回JSON格式结果。
     * 这是简化版接口，供百宝箱工作流直接调用。
     * 完整版搜索（支持 keywords/jobType/company）走 JobAgentService.extractSearchParams。
     * </p>
     *
     * @param keyword 搜索关键词（可选，如"前端"、"Java"）
     * @param city    城市（可选，如"北京"）
     * @param userId  用户ID（用于日志追踪）
     * @return 岗位列表JSON（PageResult格式：{list:[...], total:N, page:1, pageSize:10}）
     */
    @GetMapping("/search")
    public Result<String> searchJobs(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "2") Long userId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("city", city);
        params.put("page", 1);
        params.put("size", 10);
        return Result.success(searchJobsTool.execute(params));
    }

    /**
     * 获取岗位详情（百宝箱工具回调）
     *
     * @param jobId 岗位ID
     * @return 岗位详情JSON（含技能要求、薪资、JD等）
     */
    @GetMapping("/detail")
    public Result<String> getJobDetail(@RequestParam Long jobId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jobId", jobId);
        return Result.success(getJobDetailTool.execute(params));
    }

    /**
     * 获取用户简历（百宝箱工具回调）
     *
     * @param userId 用户ID
     * @return 简历信息JSON（含技能、经历、教育等）
     */
    @GetMapping("/resume")
    public Result<String> getResume(@RequestParam(defaultValue = "2") Long userId) {
        return Result.success(getMyResumeTool.execute(userId));
    }

    /**
     * 计算匹配度（百宝箱工具回调）
     * <p>
     * 计算用户与岗位的匹配度，返回详细匹配结果（6维度评分）。
     * </p>
     *
     * @param jobId  岗位ID
     * @param userId 用户ID
     * @return 匹配结果JSON（matchScore、matchedSkills、missingSkills等）
     */
    @GetMapping("/match")
    public Result<String> calculateMatch(
            @RequestParam Long jobId,
            @RequestParam(defaultValue = "2") Long userId) {
        return Result.success(calculateMatchTool.execute(jobId, userId));
    }

    /**
     * 查询企业岗位（百宝箱工具回调）
     * <p>
     * 按企业名称查询该企业的所有岗位。
     * </p>
     *
     * @param company 企业名称（如"字节跳动"）
     * @return 企业岗位列表JSON
     */
    @GetMapping("/company")
    public Result<String> getCompanyJobs(@RequestParam String company) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("company", company);
        params.put("page", 1);
        params.put("size", 10);
        return Result.success(getCompanyJobsTool.execute(params));
    }

    /**
     * 智能推荐（百宝箱工具回调）
     * <p>
     * 一次性获取用户简历和岗位列表，组装成推荐上下文返回。
     * Agent收到后会进一步筛选和排序。
     * </p>
     *
     * @param userId 用户ID
     * @return 简历信息 + 岗位列表的组合文本
     */
    @GetMapping("/recommend")
    public Result<String> recommend(@RequestParam(defaultValue = "2") Long userId) {
        // ① 获取简历
        String resume = getMyResumeTool.execute(userId);

        // ② 搜索岗位
        Map<String, Object> searchParams = new LinkedHashMap<>();
        searchParams.put("keyword", "");
        searchParams.put("city", "");
        searchParams.put("page", 1);
        searchParams.put("size", 10);
        String jobs = searchJobsTool.execute(searchParams);

        // ③ 组装结果
        return Result.success("简历信息：\n" + resume + "\n\n搜索结果：\n" + jobs);
    }

    /**
     * 内部接口：计算匹配度（返回纯分数）
     * <p>
     * 供 lingxi-resume 投递时调用，返回0-100的匹配分数。
     * 与 /match 接口的区别：本接口只返回分数，/match 返回详细匹配结果。
     * </p>
     *
     * @param params 请求体：{"jobId": 1, "userId": 2}
     * @return 匹配分数（0-100）
     */
    @PostMapping("/match-score")
    public Result<Integer> calculateMatchScore(@RequestBody Map<String, Long> params) {
        Long jobId = params.get("jobId");
        Long userId = params.get("userId");
        if (jobId == null || userId == null) {
            return Result.error(400, "jobId 和 userId 不能为空");
        }

        String resultJson = calculateMatchTool.execute(jobId, userId);
        try {
            // 解析 matchScore
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> result = mapper.readValue(resultJson, Map.class);
            Object score = result.get("matchScore");
            if (score instanceof Number) {
                return Result.success(((Number) score).intValue());
            }
            return Result.success(0);
        } catch (Exception e) {
            log.warn("解析匹配度结果失败: {}", resultJson, e);
            return Result.success(0);
        }
    }
}

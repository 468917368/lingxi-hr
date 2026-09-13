package com.lingxi.user.agent.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.user.agent.tools.CalculateMatchTool;
import com.lingxi.user.agent.tools.SearchJobsTool;
import com.lingxi.user.feign.ChatFeignClient;
import com.lingxi.user.feign.ResumeFeignClient;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 岗位推荐服务
 *
 * 复用现有工具实现岗位推荐：
 * - CalculateMatchTool：匹配度计算
 * - SearchJobsTool：岗位搜索
 * - ChatFeignClient：通知发送（调用聊天服务）
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobRecommendService {

    /** 复用现有工具 */
    private final CalculateMatchTool calculateMatchTool;
    private final SearchJobsTool searchJobsTool;
    private final ResumeFeignClient resumeFeignClient;
    private final ChatFeignClient chatFeignClient;
    private final ObjectMapper objectMapper;

    /** 每日推荐岗位数量 */
    private static final int DAILY_RECOMMEND_COUNT = 3;

    /**
     * 为用户计算推荐并发送通知
     *
     * @param userId 用户ID
     * @return 是否发送了通知
     */
    public boolean recommendAndNotify(Long userId) {
        // 1. 检查用户是否有简历
        if (!hasResume(userId)) {
            log.debug("用户无简历，跳过推荐: userId={}", userId);
            return false;
        }

        // 2. 搜索候选岗位
        List<Map<String, Object>> candidateJobs = searchCandidateJobs();
        if (candidateJobs.isEmpty()) {
            log.debug("无候选岗位，跳过推荐: userId={}", userId);
            return false;
        }
        log.info("候选岗位数: userId={}, count={}", userId, candidateJobs.size());

        // 3. 排除已投递岗位
        Set<Long> appliedJobIds = getAppliedJobIds(userId);
        candidateJobs.removeIf(job -> {
            Long jobId = Long.valueOf(job.get("jobId").toString());
            return appliedJobIds.contains(jobId);
        });
        log.info("排除已投递后: userId={}, count={}", userId, candidateJobs.size());

        // 4. 计算匹配度并排序，取Top3
        List<JobRecommendVO> recommendations = calculateAndRank(userId, candidateJobs, DAILY_RECOMMEND_COUNT);
        if (recommendations.isEmpty()) {
            log.debug("无推荐结果，跳过: userId={}", userId);
            return false;
        }
        log.info("推荐岗位数: userId={}, count={}", userId, recommendations.size());

        // 4. 清除旧推荐通知
        chatFeignClient.deleteTodayNotifications(userId, "JOB_RECOMMEND");

        // 5. 发送新推荐通知
        sendNotifications(userId, recommendations);

        log.info("推荐通知发送成功: userId={}, count={}", userId, recommendations.size());
        return true;
    }

    /**
     * 获取用户已投递的岗位ID列表
     */
    private Set<Long> getAppliedJobIds(Long userId) {
        try {
            Result<List<Long>> result = resumeFeignClient.getAppliedJobIds(userId);
            List<Long> appliedIds = result.getData();
            return new HashSet<>(appliedIds != null ? appliedIds : Collections.emptyList());
        } catch (Exception e) {
            log.warn("获取已投递岗位失败: userId={}", userId, e);
            return Collections.emptySet();
        }
    }

    /**
     * 检查用户是否有简历
     */
    private boolean hasResume(Long userId) {
        try {
            Result<Map<String, Object>> result = resumeFeignClient.getUserResume(userId);
            Map<String, Object> data = result.getData();
            return data != null && !data.isEmpty() && !Boolean.FALSE.equals(data.get("hasResume"));
        } catch (Exception e) {
            log.warn("检查简历失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 搜索候选岗位
     */
    private List<Map<String, Object>> searchCandidateJobs() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "");
        params.put("city", "");
        params.put("page", 1);
        params.put("size", 100);

        String json = searchJobsTool.execute(params);

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> jobs = new ArrayList<>();
            for (JsonNode node : listNode) {
                Map<String, Object> job = objectMapper.convertValue(node, Map.class);
                if (job.get("jobId") != null) {
                    jobs.add(job);
                }
            }
            return jobs;
        } catch (Exception e) {
            log.warn("解析岗位列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 计算匹配度并排序
     */
    private List<JobRecommendVO> calculateAndRank(Long userId, List<Map<String, Object>> jobs, int topN) {
        List<JobRecommendVO> recommendations = new ArrayList<>();

        for (Map<String, Object> job : jobs) {
            try {
                Long jobId = Long.valueOf(job.get("jobId").toString());

                // 复用 CalculateMatchTool 计算匹配度
                String matchResult = calculateMatchTool.execute(jobId, userId);
                JsonNode node = objectMapper.readTree(matchResult);

                if (node.has("matchScore")) {
                    int matchScore = node.get("matchScore").asInt();
                    if (matchScore > 0) {
                        JobRecommendVO vo = new JobRecommendVO();
                        vo.setJobId(jobId);
                        vo.setTitle((String) job.get("title"));
                        vo.setCompanyName((String) job.get("companyName"));
                        vo.setCityName((String) job.get("cityName"));
                        vo.setSalaryRange(formatSalary(job));
                        vo.setMatchScore(matchScore);
                        recommendations.add(vo);
                    }
                }
            } catch (Exception e) {
                log.warn("计算匹配度失败: jobId={}", job.get("jobId"), e);
            }
        }

        // 按匹配度排序，取TopN
        recommendations.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        return recommendations.subList(0, Math.min(topN, recommendations.size()));
    }

    /**
     * 发送通知
     */
    private void sendNotifications(Long userId, List<JobRecommendVO> recommendations) {
        for (JobRecommendVO job : recommendations) {
            String title = "【推荐】" + job.getTitle() + " @" + job.getCompanyName();

            StringBuilder content = new StringBuilder();
            content.append("匹配度：").append(job.getMatchScore()).append("%");
            if (job.getSalaryRange() != null) {
                content.append("  💰 ").append(job.getSalaryRange());
            }
            if (job.getCityName() != null) {
                content.append("  📍 ").append(job.getCityName());
            }
            content.append("\n点击查看详情，投递心仪的岗位！");

            // 通过 Feign 调用聊天服务发送通知
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("type", "JOB_RECOMMEND");
            request.put("title", title);
            request.put("content", content.toString());
            request.put("targetType", "JOB");
            request.put("targetId", job.getJobId());

            chatFeignClient.createNotification(request);
        }
    }

    /**
     * 格式化薪资
     */
    private String formatSalary(Map<String, Object> job) {
        Object min = job.get("salaryMinAmount");
        Object max = job.get("salaryMaxAmount");
        if (min == null || max == null) return null;

        try {
            long minK = ((Number) min).longValue() / 1000;
            long maxK = ((Number) max).longValue() / 1000;
            return minK + "K-" + maxK + "K";
        } catch (Exception e) {
            return null;
        }
    }
}

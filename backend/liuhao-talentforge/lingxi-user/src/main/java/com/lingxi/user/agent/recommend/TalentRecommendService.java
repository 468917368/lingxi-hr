package com.lingxi.user.agent.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.user.agent.tools.CalculateMatchTool;
import com.lingxi.user.feign.ChatFeignClient;
import com.lingxi.user.feign.HrCompanyFeignClient;
import com.lingxi.user.feign.JobFeignClient;
import com.lingxi.user.feign.ResumeFeignClient;
import com.lingxi.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 人才推荐服务
 *
 * 为HR推荐匹配的求职者：
 * 1. 查询开启简历公开的求职者
 * 2. 查询HR发布的岗位
 * 3. 计算匹配度，找到5个匹配度>80%的人才
 * 4. 发送通知给HR
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentRecommendService {

    private final CalculateMatchTool calculateMatchTool;
    private final JobFeignClient jobFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final ChatFeignClient chatFeignClient;
    private final SysUserMapper userMapper;
    private final HrCompanyFeignClient hrCompanyFeignClient;
    private final ObjectMapper objectMapper;

    /** 目标推荐人数（每次只推荐1个） */
    private static final int TARGET_TALENT_COUNT = 1;
    /** 最低匹配度 */
    private static final int MIN_MATCH_SCORE = 80;
    /** 最大计算人数（避免计算过多） */
    private static final int MAX_CALCULATE_COUNT = 50;

    /**
     * 为HR推荐人才
     *
     * @param hrUserId HR用户ID
     * @return 是否发送了通知
     */
    public boolean recommendTalent(Long hrUserId) {
        log.info("开始人才推荐: hrUserId={}", hrUserId);

        // 1. 获取HR发布的岗位
        List<Map<String, Object>> hrJobs = getHrJobs(hrUserId);
        if (hrJobs.isEmpty()) {
            log.debug("HR无发布岗位，跳过推荐: hrUserId={}", hrUserId);
            return false;
        }
        log.info("HR岗位数: hrUserId={}, count={}", hrUserId, hrJobs.size());

        // 2. 获取开启简历公开的求职者
        List<Long> publicResumeUsers = userMapper.findPublicResumeCandidates();
        if (publicResumeUsers.isEmpty()) {
            log.debug("无公开简历求职者，跳过推荐");
            return false;
        }
        log.info("公开简历求职者数: {}", publicResumeUsers.size());

        // 3. 计算匹配度，找到目标人才
        List<TalentRecommendVO> talents = findMatchingTalents(hrJobs, publicResumeUsers);

        // 4. 清除旧通知
        chatFeignClient.deleteTodayNotifications(hrUserId, "TALENT_RECOMMEND");

        // 5. 发送通知
        if (!talents.isEmpty()) {
            sendTalentNotification(hrUserId, talents);
            log.info("人才推荐完成: hrUserId={}, talentCount={}", hrUserId, talents.size());
            return true;
        }

        log.info("无符合条件的人才: hrUserId={}", hrUserId);
        return false;
    }

    /**
     * 获取HR发布的岗位
     */
    private List<Map<String, Object>> getHrJobs(Long hrUserId) {
        try {
            // 获取HR所在企业ID
            Long companyId = hrCompanyFeignClient.getCompanyIdByUserId(hrUserId).getData();
            if (companyId == null) {
                return Collections.emptyList();
            }

            // 搜索该企业的岗位
            Result<com.lingxi.common.domain.PageResult<Map<String, Object>>> result =
                    jobFeignClient.searchJobs("", null, "", null, null, null, 1, 50);

            if (result.getData() == null || result.getData().getList() == null) {
                return Collections.emptyList();
            }

            // 过滤出该企业的岗位
            List<Map<String, Object>> jobs = new ArrayList<>();
            for (Map<String, Object> job : result.getData().getList()) {
                Object companyIdObj = job.get("companyId");
                if (companyIdObj != null && companyId.equals(Long.valueOf(companyIdObj.toString()))) {
                    jobs.add(job);
                }
            }
            return jobs;
        } catch (Exception e) {
            log.warn("获取HR岗位失败: hrUserId={}", hrUserId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查找匹配的人才
     */
    private List<TalentRecommendVO> findMatchingTalents(
            List<Map<String, Object>> hrJobs,
            List<Long> candidateUserIds) {

        List<TalentRecommendVO> talents = new ArrayList<>();
        Set<Long> processedUsers = new HashSet<>();

        // 获取HR岗位ID集合（用于检查候选人是否已投递）
        Set<Long> hrJobIds = new HashSet<>();
        for (Map<String, Object> job : hrJobs) {
            hrJobIds.add(Long.valueOf(job.get("jobId").toString()));
        }

        // 限制计算人数
        int maxCalc = Math.min(candidateUserIds.size(), MAX_CALCULATE_COUNT);

        for (int i = 0; i < maxCalc && talents.size() < TARGET_TALENT_COUNT; i++) {
            Long userId = candidateUserIds.get(i);

            // 跳过已处理的用户
            if (processedUsers.contains(userId)) continue;
            processedUsers.add(userId);

            // 检查候选人是否已投递过HR的岗位（已在人才库）
            if (hasAppliedToHrJobs(userId, hrJobIds)) {
                log.debug("候选人已投递过HR岗位，跳过: userId={}", userId);
                continue;
            }

            // 计算该用户与所有岗位的最高匹配度
            TalentRecommendVO bestMatch = findBestMatchForUser(userId, hrJobs);
            if (bestMatch != null && bestMatch.getMatchScore() >= MIN_MATCH_SCORE) {
                talents.add(bestMatch);
                log.debug("找到匹配人才: userId={}, score={}, job={}",
                        userId, bestMatch.getMatchScore(), bestMatch.getJobTitle());
            }
        }

        // 按匹配度排序
        talents.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        return talents;
    }

    /**
     * 为用户找到最佳匹配岗位
     */
    private TalentRecommendVO findBestMatchForUser(Long userId, List<Map<String, Object>> hrJobs) {
        TalentRecommendVO bestMatch = null;
        int bestScore = 0;

        for (Map<String, Object> job : hrJobs) {
            try {
                Long jobId = Long.valueOf(job.get("jobId").toString());

                // 复用 CalculateMatchTool 计算匹配度
                String matchResult = calculateMatchTool.execute(jobId, userId);
                JsonNode node = objectMapper.readTree(matchResult);

                if (node.has("matchScore")) {
                    int score = node.get("matchScore").asInt();
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = new TalentRecommendVO();
                        bestMatch.setUserId(userId);
                        bestMatch.setJobId(jobId);
                        bestMatch.setJobTitle((String) job.get("title"));
                        bestMatch.setMatchScore(score);
                    }
                }
            } catch (Exception e) {
                log.debug("计算匹配度失败: userId={}, jobId={}", userId, job.get("jobId"), e);
            }
        }

        return bestMatch;
    }

    /**
     * 检查候选人是否已投递过HR的岗位（已在人才库）
     */
    private boolean hasAppliedToHrJobs(Long userId, Set<Long> hrJobIds) {
        try {
            Result<List<Long>> result = resumeFeignClient.getAppliedJobIds(userId);
            List<Long> appliedJobIds = result.getData();
            if (appliedJobIds == null || appliedJobIds.isEmpty()) {
                return false;
            }
            // 检查是否有交集
            for (Long appliedJobId : appliedJobIds) {
                if (hrJobIds.contains(appliedJobId)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("检查候选人投递记录失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 发送人才推荐通知
     */
    private void sendTalentNotification(Long hrUserId, List<TalentRecommendVO> talents) {
        // 为每个推荐的人才单独发送一条通知
        for (TalentRecommendVO talent : talents) {
            String title = "【人才推荐】" + (talent.getUserName() != null ? talent.getUserName() : "求职者" + talent.getUserId());

            StringBuilder content = new StringBuilder();
            content.append("匹配岗位：").append(talent.getJobTitle());
            content.append("\n匹配度：").append(talent.getMatchScore()).append("%");
            if (talent.getDesiredJob() != null) {
                content.append("\n期望岗位：").append(talent.getDesiredJob());
            }
            if (talent.getDesiredCity() != null) {
                content.append("\n期望城市：").append(talent.getDesiredCity());
            }
            if (talent.getExpectedSalary() != null) {
                content.append("\n期望薪资：").append(talent.getExpectedSalary());
            }
            content.append("\n\n点击查看候选人详情，发起沟通！");

            // 发送通知，targetType=CANDIDATE, targetId=候选人用户ID
            Map<String, Object> request = new HashMap<>();
            request.put("userId", hrUserId);
            request.put("type", "TALENT_RECOMMEND");
            request.put("title", title);
            request.put("content", content.toString());
            request.put("targetType", "CANDIDATE");
            request.put("targetId", talent.getUserId());

            chatFeignClient.createNotification(request);
        }
    }
}

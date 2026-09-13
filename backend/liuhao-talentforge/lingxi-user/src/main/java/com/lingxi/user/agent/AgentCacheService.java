package com.lingxi.user.agent;

import com.lingxi.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 数据缓存服务
 * 缓存简历、岗位等热点数据，减少 Feign 调用
 *
 * @author 成员A
 * @since 2026-08-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCacheService {

    private final RedisUtil redisUtil;

    private static final String RESUME_KEY = "agent:resume:%d";
    private static final String JOB_KEY = "agent:job:%d";

    private static final long RESUME_TTL = 5;
    private static final long JOB_TTL = 10;

    /**
     * 获取用户简历（缓存5分钟）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserResume(Long userId, ResumeLoader loader) {
        String key = String.format(RESUME_KEY, userId);
        try {
            Map<String, Object> cached = redisUtil.get(key);
            if (cached != null) {
                log.debug("简历缓存命中: userId={}", userId);
                return cached;
            }
        } catch (Exception e) {
            log.warn("读取简历缓存失败: userId={}", userId, e);
        }

        Map<String, Object> data = loader.load();
        if (data != null && !data.isEmpty()) {
            try {
                redisUtil.set(key, data, RESUME_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("写入简历缓存失败: userId={}", userId, e);
            }
        }
        return data;
    }

    /**
     * 获取岗位详情（缓存10分钟）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobDetail(Long jobId, JobLoader loader) {
        String key = String.format(JOB_KEY, jobId);
        try {
            Map<String, Object> cached = redisUtil.get(key);
            if (cached != null) {
                log.debug("岗位缓存命中: jobId={}", jobId);
                return cached;
            }
        } catch (Exception e) {
            log.warn("读取岗位缓存失败: jobId={}", jobId, e);
        }

        Map<String, Object> data = loader.load();
        if (data != null && !data.isEmpty()) {
            try {
                redisUtil.set(key, data, JOB_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("写入岗位缓存失败: jobId={}", jobId, e);
            }
        }
        return data;
    }

    @FunctionalInterface
    public interface ResumeLoader {
        Map<String, Object> load();
    }

    @FunctionalInterface
    public interface JobLoader {
        Map<String, Object> load();
    }
}

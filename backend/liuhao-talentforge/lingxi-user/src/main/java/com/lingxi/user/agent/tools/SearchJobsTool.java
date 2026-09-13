package com.lingxi.user.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.util.RedisUtil;
import com.lingxi.user.feign.JobFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 搜索岗位工具（调用 lingxi-job 内部接口搜索岗位）
 * <p>
 * 支持的搜索条件：
 * - keywords: 扩展关键词列表（title LIKE OR 搜索，如["前端","frontend","react"]）
 * - jobType: 岗位类型枚举精确过滤（如"FRONTEND"，有 keywords 时不传）
 * - company: 公司名模糊过滤（hr_company 子查询）
 * - city: 城市过滤
 * </p>
 * <p>
 * 缓存策略：Redis 缓存 2 分钟，key 包含所有筛选参数
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchJobsTool {

    private final JobFeignClient jobFeignClient;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;

    private static final String CACHE_KEY_PREFIX = "agent:search:";
    private static final long CACHE_TTL_MINUTES = 2;

    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> params) {
        try {
            String keyword = (String) params.getOrDefault("keyword", "");
            List<String> keywords = params.get("keywords") instanceof List
                    ? (List<String>) params.get("keywords") : Collections.emptyList();
            String city = (String) params.getOrDefault("city", "");
            String jobType = (String) params.getOrDefault("jobType", "");
            String company = (String) params.getOrDefault("company", "");
            Integer page = getInt(params, "page", 1);
            Integer size = getInt(params, "size", 10);
            size = Math.min(size, 20);

            // 检查缓存（key 包含所有筛选参数）
            String cacheKey = buildCacheKey(keyword, keywords, city, jobType, company, page, size);
            try {
                String cached = redisUtil.get(cacheKey);
                if (cached != null) {
                    log.debug("搜索缓存命中: cacheKey={}", cacheKey);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("读取搜索缓存失败", e);
            }

            // 调用 Feign 搜索（传入 keywords 扩展搜索 + jobType 过滤 + 公司名过滤）
            Result<PageResult<Map<String, Object>>> result =
                    jobFeignClient.searchJobs(keyword, keywords.isEmpty() ? null : keywords,
                            city, null, jobType.isEmpty() ? null : jobType,
                            company.isEmpty() ? null : company, page, size);

            // 调试日志：打印搜索结果数量
            if (result.getData() != null) {
                List<?> list = result.getData().getList();
                log.info("搜索结果: total={}, listSize={}, company={}, keywords={}",
                        result.getData().getTotal(), list != null ? list.size() : 0, company, keywords);
            } else {
                log.warn("搜索结果: result.getData() is null, result={}", result);
            }

            String json = objectMapper.writeValueAsString(result.getData());

            // 写入缓存
            try {
                redisUtil.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("写入搜索缓存失败", e);
            }

            return json;
        } catch (Exception e) {
            log.error("搜索岗位失败", e);
            return "{\"error\": \"搜索失败\"}";
        }
    }

    private String buildCacheKey(String keyword, List<String> keywords, String city,
                                  String jobType, String company, int page, int size) {
        return CACHE_KEY_PREFIX + keyword + ":" + String.join(",", keywords)
                + ":" + city + ":" + jobType + ":" + company + ":" + page + ":" + size;
    }

    private Integer getInt(Map<String, Object> args, String key, Integer defaultVal) {
        Object val = args.get(key);
        if (val == null) return defaultVal;
        return val instanceof Number ? ((Number) val).intValue() : defaultVal;
    }
}

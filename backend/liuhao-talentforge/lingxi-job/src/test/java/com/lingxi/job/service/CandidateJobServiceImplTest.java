package com.lingxi.job.service;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.domain.Result;
import com.lingxi.job.feign.CandidateProfileFeignClient;
import com.lingxi.job.feign.UserFeignClient;
import com.lingxi.job.feign.dto.CandidateProfileDTO;
import com.lingxi.job.mapper.JobFavoriteMapper;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.service.impl.CandidateJobServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C端岗位搜索「查询分流」单元测试（阶段6.3）
 * <p>Mock JobPostMapper/JobProfileMapper/JobFavoriteMapper/CandidateProfileFeignClient，
 * 通过反射直插 UserContext 的 ThreadLocal 提供 CANDIDATE 用户（项目依赖 mockito-core 不支持
 * MockedStatic，故不用 mockStatic），验证 searchJobs 的 Mapper 分流选择：</p>
 * <ul>
 *   <li>画像全空 / Feign 失败 → selectLatestForCandidateSearch（轻量，recommendScore=0）</li>
 *   <li>画像有至少一个有效信号 → selectForCandidateSearch（推荐，profileMode=true）</li>
 * </ul>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
class CandidateJobServiceImplTest {

    /**
     * 画像全空（Feign 成功但六字段全 null）→ 无有效信号，降级最新排序
     */
    @Test
    void searchJobs_emptyProfileUsesLatestQuery() {
        JobPostMapper postMapper = mock(JobPostMapper.class);
        JobProfileMapper profileMapper = mock(JobProfileMapper.class);
        JobFavoriteMapper favoriteMapper = mock(JobFavoriteMapper.class);
        CandidateProfileFeignClient feign = mock(CandidateProfileFeignClient.class);
        UserFeignClient userFeign = mock(UserFeignClient.class);
        when(feign.getUserProfile(anyLong())).thenReturn(Result.success(new CandidateProfileDTO()));
        when(postMapper.selectLatestForCandidateSearch(anyMap())).thenReturn(Collections.emptyList());

        CandidateJobServiceImpl service =
                new CandidateJobServiceImpl(postMapper, profileMapper, favoriteMapper, feign, userFeign);
        setUser(candidateUser());
        try {
            service.searchJobs(recommendedNoFilterQuery());
        } finally {
            clearUser();
        }

        verify(postMapper).selectLatestForCandidateSearch(anyMap());
        verify(postMapper, never()).selectForCandidateSearch(anyMap());
    }

    /**
     * 画像 Feign 失败（1113 显式降级）→ 走最新排序
     */
    @Test
    void searchJobs_profileFeignFailureUsesLatestQuery() {
        JobPostMapper postMapper = mock(JobPostMapper.class);
        JobProfileMapper profileMapper = mock(JobProfileMapper.class);
        JobFavoriteMapper favoriteMapper = mock(JobFavoriteMapper.class);
        CandidateProfileFeignClient feign = mock(CandidateProfileFeignClient.class);
        UserFeignClient userFeign = mock(UserFeignClient.class);
        when(feign.getUserProfile(anyLong())).thenReturn(Result.error(1113, "用户画像不存在"));
        when(postMapper.selectLatestForCandidateSearch(anyMap())).thenReturn(Collections.emptyList());

        CandidateJobServiceImpl service =
                new CandidateJobServiceImpl(postMapper, profileMapper, favoriteMapper, feign, userFeign);
        setUser(candidateUser());
        try {
            service.searchJobs(recommendedNoFilterQuery());
        } finally {
            clearUser();
        }

        verify(postMapper).selectLatestForCandidateSearch(anyMap());
        verify(postMapper, never()).selectForCandidateSearch(anyMap());
    }

    /**
     * 画像有有效信号（desiredJob）→ 启用画像模式，走原推荐 SQL（profileMode=true）
     */
    @Test
    void searchJobs_profileWithDesiredJobUsesRecommendedQuery() {
        JobPostMapper postMapper = mock(JobPostMapper.class);
        JobProfileMapper profileMapper = mock(JobProfileMapper.class);
        JobFavoriteMapper favoriteMapper = mock(JobFavoriteMapper.class);
        CandidateProfileFeignClient feign = mock(CandidateProfileFeignClient.class);
        UserFeignClient userFeign = mock(UserFeignClient.class);
        CandidateProfileDTO p = new CandidateProfileDTO();
        p.setDesiredJob("Java");
        when(feign.getUserProfile(anyLong())).thenReturn(Result.success(p));
        when(postMapper.selectForCandidateSearch(anyMap())).thenReturn(Collections.emptyList());

        CandidateJobServiceImpl service =
                new CandidateJobServiceImpl(postMapper, profileMapper, favoriteMapper, feign, userFeign);
        setUser(candidateUser());
        try {
            service.searchJobs(recommendedNoFilterQuery());
        } finally {
            clearUser();
        }

        verify(postMapper).selectForCandidateSearch(
                argThat((Map<String, Object> q) -> Boolean.TRUE.equals(q.get("profileMode"))));
        verify(postMapper, never()).selectLatestForCandidateSearch(anyMap());
    }

    // ==================== 辅助 ====================

    /** CANDIDATE 用户上下文 */
    private static UserDTO candidateUser() {
        UserDTO user = new UserDTO();
        user.setUserId(1001L);
        user.setRole("CANDIDATE");
        return user;
    }

    /** 无筛选 RECOMMENDED 查询参数（其余筛选参数缺省即 null） */
    private static Map<String, Object> recommendedNoFilterQuery() {
        Map<String, Object> query = new HashMap<>();
        query.put("sortBy", "RECOMMENDED");
        query.put("page", 1);
        query.put("size", 20);
        return query;
    }

    /**
     * 反射直插 UserContext.USER_HOLDER ThreadLocal（mockito-core 无 inline mock maker，不支持 mockStatic）
     */
    private static void setUser(UserDTO user) {
        try {
            Field holder = UserContext.class.getDeclaredField("USER_HOLDER");
            holder.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<UserDTO> threadLocal = (ThreadLocal<UserDTO>) holder.get(null);
            if (user == null) {
                threadLocal.remove();
            } else {
                threadLocal.set(user);
            }
        } catch (Exception e) {
            throw new IllegalStateException("注入 UserContext 失败", e);
        }
    }

    /** 清理用户上下文（防 ThreadLocal 泄漏） */
    private static void clearUser() {
        setUser(null);
    }
}

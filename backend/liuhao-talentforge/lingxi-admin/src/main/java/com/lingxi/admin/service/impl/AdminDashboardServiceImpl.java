package com.lingxi.admin.service.impl;

import com.lingxi.admin.domain.vo.CompanyRankVO;
import com.lingxi.admin.domain.vo.JobTypeVO;
import com.lingxi.admin.domain.vo.OverviewVO;
import com.lingxi.admin.domain.vo.TrendVO;
import com.lingxi.admin.feign.JobFeignClient;
import com.lingxi.admin.feign.ResumeFeignClient;
import com.lingxi.admin.feign.UserFeignClient;
import com.lingxi.admin.mapper.HrDataMapper;
import com.lingxi.admin.service.AdminDashboardService;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据看板服务实现
 *
 * @author 成员E
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final JobFeignClient jobFeign;
    private final ResumeFeignClient resumeFeign;
    private final UserFeignClient userFeign;
    private final HrDataMapper hrDataMapper;

    @Override
    public OverviewVO getStats() {
        try {
            // 并行调用：用户/岗位/投递用Feign，企业/面试/Offer直接查数据库
            CompletableFuture<Long> userCount = CompletableFuture.supplyAsync(
                    () -> safeGetLong(userFeign.getUserCount()));
            CompletableFuture<Long> userCountToday = CompletableFuture.supplyAsync(
                    () -> safeGetLong(userFeign.getTodayUserCount()));
            CompletableFuture<Long> enterpriseCount = CompletableFuture.supplyAsync(
                    hrDataMapper::countEnterprises);
            CompletableFuture<Long> enterpriseCountToday = CompletableFuture.supplyAsync(
                    hrDataMapper::countTodayEnterprises);
            CompletableFuture<Long> positionCount = CompletableFuture.supplyAsync(
                    () -> extractLongValue(jobFeign.getPositionCount()));
            CompletableFuture<Long> positionCountToday = CompletableFuture.supplyAsync(
                    () -> extractLongValue(jobFeign.getTodayPositionCount()));
            CompletableFuture<Long> applicationCount = CompletableFuture.supplyAsync(
                    () -> safeGetLong(resumeFeign.getApplicationCount()));
            CompletableFuture<Map<String, Object>> applicationTrend = CompletableFuture.supplyAsync(
                    () -> safeGetMap(resumeFeign.getApplicationTrend()));
            CompletableFuture<Long> interviewCount = CompletableFuture.supplyAsync(
                    hrDataMapper::countInterviews);
            CompletableFuture<Long> interviewCountToday = CompletableFuture.supplyAsync(
                    hrDataMapper::countTodayInterviews);
            CompletableFuture<Long> offerCount = CompletableFuture.supplyAsync(
                    hrDataMapper::countOffers);
            CompletableFuture<Long> offerCountToday = CompletableFuture.supplyAsync(
                    hrDataMapper::countTodayOffers);

            // 等待全部完成
            CompletableFuture.allOf(
                    userCount, userCountToday, enterpriseCount, enterpriseCountToday,
                    positionCount, positionCountToday, applicationCount, applicationTrend,
                    interviewCount, interviewCountToday, offerCount, offerCountToday
            ).join();

            // 从趋势数据中获取今日投递数
            Map<String, Object> trendData = applicationTrend.get();
            Integer applicationCountToday = 0;
            if (trendData != null && trendData.containsKey("todayCount")) {
                applicationCountToday = (Integer) trendData.get("todayCount");
            }

            // 聚合为OverviewVO
            OverviewVO vo = new OverviewVO();
            vo.setUserCount(userCount.get().intValue());
            vo.setUserCountToday(userCountToday.get().intValue());
            vo.setUserCountGrowth(calcGrowth(vo.getUserCountToday(), vo.getUserCount()));

            vo.setEnterpriseCount(enterpriseCount.get().intValue());
            vo.setEnterpriseCountToday(enterpriseCountToday.get().intValue());
            vo.setEnterpriseCountGrowth(calcGrowth(vo.getEnterpriseCountToday(), vo.getEnterpriseCount()));

            vo.setPositionCount(positionCount.get().intValue());
            vo.setPositionCountToday(positionCountToday.get().intValue());
            vo.setPositionCountGrowth(calcGrowth(vo.getPositionCountToday(), vo.getPositionCount()));

            vo.setApplicationCount(applicationCount.get().intValue());
            vo.setApplicationCountToday(applicationCountToday);
            vo.setApplicationCountGrowth(calcGrowth(vo.getApplicationCountToday(), vo.getApplicationCount()));

            vo.setInterviewCount(interviewCount.get().intValue());
            vo.setInterviewCountToday(interviewCountToday.get().intValue());
            vo.setInterviewCountGrowth(calcGrowth(vo.getInterviewCountToday(), vo.getInterviewCount()));

            vo.setOfferCount(offerCount.get().intValue());
            vo.setOfferCountToday(offerCountToday.get().intValue());
            vo.setOfferCountGrowth(calcGrowth(vo.getOfferCountToday(), vo.getOfferCount()));

            return vo;

        } catch (Exception e) {
            log.error("获取数据看板统计失败", e);
            // 返回空数据
            return new OverviewVO();
        }
    }

    @Override
    public TrendVO getTrend() {
        Result<Map<String, Object>> result = resumeFeign.getApplicationTrend();
        if (result == null || !result.isSuccess() || result.getData() == null) {
            TrendVO vo = new TrendVO();
            vo.setDates(new ArrayList<>());
            vo.setCounts(new ArrayList<>());
            vo.setTotal(null);  // null 表示降级，前端可区分"暂无数据" vs "服务不可用"
            vo.setAverage(null);
            return vo;
        }
        Map<String, Object> data = result.getData();
        TrendVO vo = new TrendVO();
        vo.setDates((List<String>) data.get("dates"));
        vo.setCounts((List<Integer>) data.get("counts"));
        vo.setTotal((Integer) data.get("total"));
        vo.setAverage((Integer) data.get("average"));
        return vo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<JobTypeVO> getJobDistribution() {
        Result<Map<String, Object>> result = jobFeign.getJobTypeDistribution();
        if (result == null || result.getData() == null) {
            return new ArrayList<>();
        }
        Map<String, Object> data = result.getData();
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<JobTypeVO> list = new ArrayList<>();
        for (Map<String, Object> item : items) {
            JobTypeVO vo = new JobTypeVO();
            vo.setType((String) item.get("jobType"));
            Object countObj = item.get("count");
            vo.setCount(countObj instanceof Number ? ((Number) countObj).intValue() : 0);
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<CompanyRankVO> getCompanyRank() {
        List<Map<String, Object>> list = hrDataMapper.getCompanyRankByConversation();
        List<CompanyRankVO> result = new ArrayList<>();
        for (Map<String, Object> item : list) {
            CompanyRankVO vo = new CompanyRankVO();
            vo.setId(((Number) item.get("id")).longValue());
            vo.setName((String) item.get("name"));
            vo.setConversationCount(((Number) item.get("conversationCount")).intValue());
            result.add(vo);
        }
        return result;
    }

    /**
     * 从 Result<Long> 中安全提取值
     */
    private Long safeGetLong(Result<Long> result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return 0L;
        }
        return result.getData();
    }

    /**
     * 从 {data: {value: 189}} 契约中提取 Long 值
     */
    private Long extractLongValue(Result<Map<String, Object>> result) {
        if (result == null || result.getData() == null) {
            return 0L;
        }
        Object value = result.getData().get("value");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private Map<String, Object> safeGetMap(Result<Map<String, Object>> result) {
        if (result == null || result.getData() == null) {
            return new java.util.HashMap<>();
        }
        return result.getData();
    }

    private Double calcGrowth(Integer today, Integer total) {
        if (total == null || total == 0) return 0.0;
        return Math.round(today * 1000.0 / total) / 10.0;
    }
}

package com.lingxi.hr.agent.mapper;

import com.lingxi.hr.agent.entity.MockReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模拟面试报告 Mapper
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Mapper
public interface MockReportMapper {

    int insert(MockReport report);

    MockReport selectBySessionId(@Param("sessionId") String sessionId);
}

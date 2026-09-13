package com.lingxi.resume.mapper;

import com.lingxi.resume.domain.entity.ResumeStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 投递状态变更日志Mapper
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Mapper
public interface ResumeStatusLogMapper {

    /**
     * 根据投递ID查询状态时间线（按变更时间正序）
     */
    List<ResumeStatusLog> selectByApplicationId(@Param("applicationId") Long applicationId);

    /**
     * 根据幂等键查询（uk_idempotent 防重复）
     */
    ResumeStatusLog selectByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    /**
     * 插入变更日志
     */
    int insert(ResumeStatusLog statusLog);
}

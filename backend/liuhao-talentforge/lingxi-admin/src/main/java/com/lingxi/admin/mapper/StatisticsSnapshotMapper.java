package com.lingxi.admin.mapper;

import com.lingxi.admin.domain.entity.StatisticsSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 统计快照Mapper
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Mapper
public interface StatisticsSnapshotMapper {

    /**
     * 根据日期查询统计快照
     *
     * @param snapshotDate 快照日期
     * @return 统计快照
     */
    StatisticsSnapshot selectByDate(@Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 插入统计快照
     *
     * @param snapshot 统计快照
     * @return 影响行数
     */
    int insert(StatisticsSnapshot snapshot);
}

package com.lingxi.hr.agent.mapper;

import com.lingxi.hr.agent.entity.MockSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 模拟面试会话 Mapper
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Mapper
public interface MockSessionMapper {

    int insert(MockSession session);

    MockSession selectById(@Param("id") Long id);

    MockSession selectBySessionId(@Param("sessionId") String sessionId);

    int updateById(MockSession session);

    /**
     * 统计求职者在指定时间之后创建的会话数（用于每日次数限制）
     */
    int countByCandidateIdAndStartAt(@Param("candidateId") Long candidateId,
                                     @Param("startAt") LocalDateTime startAt);
}

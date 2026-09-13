package com.lingxi.hr.agent.mapper;

import com.lingxi.hr.agent.entity.MockAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模拟面试答题记录 Mapper
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Mapper
public interface MockAnswerMapper {

    int insert(MockAnswer answer);

    List<MockAnswer> selectBySessionId(@Param("sessionId") String sessionId);

    MockAnswer selectBySessionIdAndQuestionNumber(@Param("sessionId") String sessionId,
                                                  @Param("questionNumber") Integer questionNumber);

    /**
     * 同题覆盖更新（系分 5.7：以最后一次提交覆盖）
     */
    int updateAnswer(MockAnswer answer);
}

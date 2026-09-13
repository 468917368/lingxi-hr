package com.lingxi.resume.mq;

import com.lingxi.common.constant.RocketMQConstant;
import com.lingxi.common.domain.InterviewEvent;
import com.lingxi.resume.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 面试评估事件消费者
 *
 * <p>topic: interview-event；tag: INTERVIEW_EVALUATED；消费组: resume-service-consumer。
 * 面试结束（成员D 面试模块）后，PASS → OFFERABLE / FAIL → REJECTED。
 *
 * <p>幂等：状态流转本身经 {@code uk_idempotent} + 乐观锁防重；重复/乱序消息
 * 触发状态冲突时仅告警（见 Service.handleInterviewEvaluated 的 catch）。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.TOPIC_INTERVIEW_EVENT,
        selectorExpression = RocketMQConstant.TAG_INTERVIEW_EVALUATED,
        consumerGroup = RocketMQConstant.CONSUMER_GROUP_RESUME
)
public class InterviewEventConsumer implements RocketMQListener<InterviewEvent> {

    private final ApplicationService applicationService;

    @Override
    public void onMessage(InterviewEvent event) {
        log.info("收到面试评估事件: interviewId={}, applicationId={}, result={}",
                event.getInterviewId(), event.getApplicationId(), event.getResult());
        if (event.getApplicationId() == null || event.getResult() == null) {
            log.warn("面试评估事件: 消息字段缺失, event={}", event);
            return;
        }
        applicationService.handleInterviewEvaluated(event.getApplicationId(), event.getResult());
    }
}

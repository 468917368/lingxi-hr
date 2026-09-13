package com.lingxi.resume.mq;

import com.lingxi.common.constant.RocketMQConstant;
import com.lingxi.common.domain.ApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 投递事件 RocketMQ 生产者
 *
 * <p>topic: application-event；tag: NEW_APPLICATION / APPLICATION_WITHDRAWN。
 * 消息体复用 common 的 {@link ApplicationEvent}。
 * 仅在业务事务提交后调用（见 Service 的 afterCommit 注册）。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送新投递事件
     */
    public void sendNewApplication(Long applicationId, Long candidateId, Long jobId) {
        ApplicationEvent event = new ApplicationEvent();
        event.setEventType(RocketMQConstant.TAG_NEW_APPLICATION);
        event.setApplicationId(applicationId);
        event.setCandidateId(candidateId);
        event.setJobId(jobId);
        event.setEventTime(LocalDateTime.now());
        send(event);
    }

    /**
     * 发送投递撤回事件
     */
    public void sendApplicationWithdrawn(Long applicationId, Long candidateId, Long jobId) {
        ApplicationEvent event = new ApplicationEvent();
        event.setEventType(RocketMQConstant.TAG_APPLICATION_WITHDRAWN);
        event.setApplicationId(applicationId);
        event.setCandidateId(candidateId);
        event.setJobId(jobId);
        event.setEventTime(LocalDateTime.now());
        send(event);
    }

    private void send(ApplicationEvent event) {
        String destination = RocketMQConstant.TOPIC_APPLICATION_EVENT + ":" + event.getEventType();
        try {
            rocketMQTemplate.convertAndSend(destination, event);
            log.info("投递事件发送成功: destination={}, applicationId={}",
                    destination, event.getApplicationId());
        } catch (Exception e) {
            // MQ 发送失败不影响主流程（投递闭环已落库），仅告警
            log.error("投递事件发送失败: destination={}, applicationId={}",
                    destination, event.getApplicationId(), e);
        }
    }
}

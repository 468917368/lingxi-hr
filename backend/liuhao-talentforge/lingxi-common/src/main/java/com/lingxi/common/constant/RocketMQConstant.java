package com.lingxi.common.constant;

/**
 * RocketMQ 常量
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public class RocketMQConstant {

    private RocketMQConstant() {
    }

    /** 投递事件Topic */
    public static final String TOPIC_APPLICATION_EVENT = "application-event";

    /** 诊断事件Topic */
    public static final String TOPIC_DIAGNOSIS_EVENT = "diagnosis-event";

    /** 面试事件Topic */
    public static final String TOPIC_INTERVIEW_EVENT = "interview-event";

    /** 投递事件Tag */
    public static final String TAG_NEW_APPLICATION = "NEW_APPLICATION";
    public static final String TAG_APPLICATION_WITHDRAWN = "APPLICATION_WITHDRAWN";

    /** 诊断事件Tag */
    public static final String TAG_DIAGNOSIS_COMPLETE = "DIAGNOSIS_COMPLETE";

    /** 面试事件Tag */
    public static final String TAG_INTERVIEW_EVALUATED = "INTERVIEW_EVALUATED";

    /** 消费者组 */
    public static final String CONSUMER_GROUP_HR = "hr-service-consumer";
    public static final String CONSUMER_GROUP_RESUME = "resume-service-consumer";
}

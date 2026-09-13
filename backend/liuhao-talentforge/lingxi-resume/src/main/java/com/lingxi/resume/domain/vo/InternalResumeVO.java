package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 简历内部视图（供 lingxi-job / lingxi-hr 等内部模块 Feign 调用）
 *
 * <p>仅返回非敏感字段：id / parseStatus / cardStructure / resumeMdUrl / candidateName。
 * 不含 phone / email / wechat 等联系方式，遵循最小权限原则——内部调用方不需要的不传输。
 *
 * <p>对齐 B 成员《2026-08-05-resume内部简历详情接口方案》契约：
 * lingxi-job 面试出题仅需 cardStructure（个性化出题），id + parseStatus 用于状态校验。
 *
 * @author 成员C
 * @since 2026-08-05
 */
@Data
public class InternalResumeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简历ID（雪花ID，字符串序列化防精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 解析状态：PENDING / PARSING / COMPLETED / FAILED */
    private String parseStatus;

    /** 卡片渲染结构（JSON 对象，sections[].points[].text） */
    private Object cardStructure;

    /** Agent 解析产出的 MD 文件 URL（供 LLM 上下文使用） */
    private String resumeMdUrl;

    /** 候选人姓名（非敏感，用于展示） */
    private String candidateName;
}

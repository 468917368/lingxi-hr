package com.lingxi.hr.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 落选反馈（写入 resume_application.reject_feedback，JSON）
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
public class RejectFeedbackDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 落选原因 */
    private String reason;

    /** 2-3 条提升建议 */
    private List<String> suggestions;
}

package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Interview Agent 出题请求 DTO（F-17）
 * <p>
 * difficulty 走 {@code DifficultyEnum} 规范化：null/空串→MEDIUM、trim+转大写、非法值→400。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class GenerateQuestionsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    @NotNull(message = "投递记录ID不能为空")
    private Long applicationId;

    /** 难度（可选，默认 MEDIUM） */
    private String difficulty;
}

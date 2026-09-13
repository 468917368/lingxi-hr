package com.lingxi.job.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lingxi.job.domain.dto.EvaluationPointDTO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

/**
 * 面试官提交 AI 面试题申请入库请求（阶段6.3 二期）
 * <p>仅含一题业务内容；出题岗位用 jobId（路径变量）。不允许任何控制字段
 * （companyId/createdBy/source/status/reviewedBy/reviewedAt/reviewReason/jobType/skillTags）；
 * 后端一律从 UserContext / 岗位画像推导，不信任前端。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Data
public class AgentQuestionSubmitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目类型（必填，BASIC/PROJECT/BOUNDARY/COMPREHENSIVE） */
    @NotBlank(message = "题目类型不能为空")
    private String questionType;

    /** 难度（可选，null/空→MEDIUM，非法→400） */
    private String difficulty;

    /** AI 生成题干（必填，≤2000） */
    @NotBlank(message = "题干不能为空")
    private String content;

    /** 考察要点（可选，≤1000，落库前逐条二次脱敏并按换行拼接）
     *  <p>兼容 SSE result 的 JSON 数组 {@code ["要点1","要点2"]} 与历史单字符串两种形态：
     *  数组→逐条处理，字符串→视为单条（{@code ACCEPT_SINGLE_VALUE_AS_ARRAY}）。</p> */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> keyPoints;

    /** 参考答案（可选，≤4000，落库前二次脱敏） */
    private String referenceAnswer;

    /** 评分要点（可选，与 SSE result 字段名 evaluationDimensions 一致，落库前 name 二次脱敏） */
    private List<EvaluationPointDTO> evaluationDimensions;
}

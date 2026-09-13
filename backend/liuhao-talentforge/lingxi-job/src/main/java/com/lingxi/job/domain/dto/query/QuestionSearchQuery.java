package com.lingxi.job.domain.dto.query;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题库搜索查询条件（百宝箱 Tool3）
 * <p>
 * 类型化查询参数，替代裸 {@code Map<String,Object>} 在 Controller→Mapper→XML 间传递。
 * companyId 由 runToken 上下文提供，不从 Query 传。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class QuestionSearchQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID（必传，数据隔离） */
    private Long companyId;

    /** 标准化岗位类型 */
    private String jobType;

    /** 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE */
    private String questionType;

    /** 难度：EASY/MEDIUM/HARD */
    private String difficulty;

    /** 标准化技能标签列表 */
    private List<String> skillTags;
}

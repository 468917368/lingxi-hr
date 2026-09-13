package com.lingxi.job.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * C端岗位搜索静态选项响应
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobOptionsResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行业选项 */
    private List<IndustryOption> industries;

    /** 排序选项 */
    private List<SortOption> sortOptions;

    /** 城市选项（从已发布岗位去重读取，避免前端硬编码） */
    private List<CityOption> cities;

    /** 技能标签（已发布岗位画像 coreSkills 提取，去空去重排序，避免前端硬编码） */
    private List<String> skills;

    /**
     * 行业选项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryOption implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 一级行业编码 */
        private String groupCode;

        /** 具体行业编码（第一版与一级行业相同） */
        private String code;

        /** 行业展示名称 */
        private String name;
    }

    /**
     * 排序选项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortOption implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 排序编码 */
        private String code;

        /** 排序展示名称 */
        private String name;
    }

    /**
     * 城市选项（code=city_code，name=city_name）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CityOption implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 城市编码 */
        private String code;

        /** 城市展示名称 */
        private String name;
    }
}

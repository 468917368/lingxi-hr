package com.lingxi.job.mapper;

import com.lingxi.job.domain.entity.JobCityDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 城市主数据字典 Mapper
 * <p>job_city_dict 为城市名称唯一真值表：selectEnabledAll 供 HR/C 端选项下拉；
 * selectByCode 含停用城市，供创建/更新校验与回填区分。</p>
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Mapper
public interface JobCityDictMapper {

    /**
     * 全部启用城市（status=1）
     */
    List<JobCityDict> selectEnabledAll();

    /**
     * 按编码查询（含停用，供校验与回填区分）
     */
    JobCityDict selectByCode(@Param("code") String code);
}

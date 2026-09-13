package com.lingxi.job.domain.dto.response;

import com.lingxi.job.domain.vo.JobTypeDistributionVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 岗位类型分布响应（成员 E 依赖）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobTypeDistributionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分布明细列表 */
    private List<JobTypeDistributionVO> items;
}

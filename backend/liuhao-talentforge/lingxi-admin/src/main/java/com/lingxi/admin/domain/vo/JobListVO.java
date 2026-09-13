package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 岗位列表响应VO
 *
 * @author 成员E
 * @since 2026-08-04
 */
@Data
public class JobListVO {

    /** 企业分组列表 */
    private List<EnterpriseGroupVO<JobVO>> groups;

    /** 岗位总数 */
    private Integer total;

    public JobListVO() {
    }

    public JobListVO(List<EnterpriseGroupVO<JobVO>> groups) {
        this.groups = groups;
        this.total = groups.stream()
                .mapToInt(g -> g.getUsers() != null ? g.getUsers().size() : 0)
                .sum();
    }
}

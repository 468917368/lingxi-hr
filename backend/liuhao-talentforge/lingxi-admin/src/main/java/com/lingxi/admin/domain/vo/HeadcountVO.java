package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * HC详情VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class HeadcountVO {

    /** 总HC */
    private Integer total;

    /** 已确认 */
    private Integer confirmed;

    /** 已预留 */
    private Integer reserved;

    /** 可用 */
    private Integer available;
}

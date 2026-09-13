package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 公告已读统计VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AnnouncementReadCountVO {

    /** 总人数 */
    private Integer totalCount;

    /** 已读人数 */
    private Integer readCount;

    /** 已读率 */
    private Double readRate;
}

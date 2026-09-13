package com.lingxi.hr.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发起 Offer 出参（系分 5.5.4）
 * <p>offerId 为 Snowflake 大数，JSON 序列化为字符串；salaryWarning 为薪资超范围软提示（不拦截）。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class OfferCreateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Offer ID（Snowflake，JSON 字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long offerId;

    /** Offer状态：SENT */
    private String status;

    /** 有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 通知是否发送成功（best-effort） */
    private Boolean notificationSent;

    /** 薪资超岗位范围软提示（不拦截；未超时 warn=false 或为 null） */
    private SalaryWarning salaryWarning;

    @Data
    public static class SalaryWarning implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 是否超出岗位薪资范围 */
        private boolean warn;

        /** 提示文案 */
        private String message;

        public static SalaryWarning of(String message) {
            SalaryWarning w = new SalaryWarning();
            w.setWarn(true);
            w.setMessage(message);
            return w;
        }
    }
}

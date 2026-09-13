package com.lingxi.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public class DateUtil {

    private DateUtil() {
    }

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_PATTERN);

    /**
     * 格式化日期时间
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DEFAULT_FORMATTER);
    }

    /**
     * 格式化日期时间（自定义格式）
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime == null ? null : dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 解析日期时间
     */
    public static LocalDateTime parse(String dateTimeStr) {
        return dateTimeStr == null ? null : LocalDateTime.parse(dateTimeStr, DEFAULT_FORMATTER);
    }

    /**
     * 获取当前时间字符串
     */
    public static String now() {
        return LocalDateTime.now().format(DEFAULT_FORMATTER);
    }
}

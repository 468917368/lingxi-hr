package com.lingxi.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花ID工具类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public class SnowflakeIdUtil {

    private SnowflakeIdUtil() {
    }

    // workerId=1, datacenterId=1（生产环境应从配置中心获取，确保集群内唯一）
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    /**
     * 生成雪花ID
     */
    public static synchronized long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 生成雪花ID（字符串）
     */
    public static synchronized String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }
}

package com.easypan.entity.constants;

/**
 * 缓存 TTL 分级常量.
 *
 */
public class CacheTTL {

    /**
     * 热数据：1 小时.
     */
    public static final int HOT_DATA = 60 * 60;

    /**
     * 温数据：6 小时.
     */
    public static final int WARM_DATA = 6 * 60 * 60;

    /**
     * 冷数据：1 天.
     */
    public static final int COLD_DATA = 24 * 60 * 60;

    /**
     * 系统配置：30 分钟.
     */
    public static final int SYS_CONFIG = 30 * 60;

    private CacheTTL() {
    }
}


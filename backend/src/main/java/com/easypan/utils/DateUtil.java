package com.easypan.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 日期工具类，提供日期格式化和解析功能.
 *
 * <p>使用线程安全的 {@link DateTimeFormatter} 替代 {@link java.text.SimpleDateFormat}，
 * 彻底消除 ThreadLocal 和 synchronized，在虚拟线程（Project Loom）下无 Pinning 风险。
 */
public class DateUtil {

    private static final Logger logger = LoggerFactory.getLogger(DateUtil.class);

    /**
     * DateTimeFormatter 缓存（线程安全、不可变，无需 ThreadLocal）.
     */
    private static final ConcurrentMap<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取或创建 DateTimeFormatter 实例.
     *
     * @param pattern 日期格式模式
     * @return 线程安全的 DateTimeFormatter
     */
    private static DateTimeFormatter getFormatter(String pattern) {
        return FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    /**
     * 格式化日期.
     *
     * @param date    日期对象
     * @param pattern 格式模式
     * @return 格式化后的字符串
     */
    public static String format(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
        return ldt.format(getFormatter(pattern));
    }

    /**
     * 解析日期字符串.
     *
     * @param dateStr 日期字符串
     * @param pattern 格式模式
     * @return 日期对象
     */
    public static Date parse(String dateStr, String pattern) {
        try {
            // 尝试用 LocalDateTime 解析（包含时分秒的格式）
            LocalDateTime ldt = LocalDateTime.parse(dateStr, getFormatter(pattern));
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e1) {
            try {
                // 回退：用 LocalDate 解析（仅日期格式如 yyyy-MM-dd）
                LocalDate ld = LocalDate.parse(dateStr, getFormatter(pattern));
                return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (Exception e2) {
                logger.error("日期解析失败: dateStr={}, pattern={}", dateStr, pattern, e2);
            }
        }
        return new Date();
    }

    /**
     * 获取指定天数后的日期.
     *
     * @param day 天数
     * @return 日期对象
     */
    public static Date getAfterDate(Integer day) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, day);
        return calendar.getTime();
    }
}


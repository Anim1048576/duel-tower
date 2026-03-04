package com.example.dueltower.common.util;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** 시간 정규화 유틸 */
public class TimeUtility {
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    public static String formatTimestamp(Timestamp ts) {
        return formatTimestamp(ts, DEFAULT_PATTERN, DEFAULT_ZONE);
    }

    public static String formatTimestamp(Timestamp ts, String pattern) {
        return formatTimestamp(ts, pattern, DEFAULT_ZONE);
    }

    public static String formatTimestamp(Timestamp ts, String pattern, ZoneId zoneId) {
        // null 보정
        if (ts == null) return "null";
        String p = (pattern == null || pattern.isBlank()) ? DEFAULT_PATTERN : pattern;
        ZoneId z = (zoneId == null) ? DEFAULT_ZONE : zoneId;

        // Timestamp -> Instant(절대시간, UTC 기준의 '순간') -> ZonedDateTime(타임존 적용된 달력 시간)
        // - 같은 순간이라도 타임존에 따라 "표시되는 시각"이 달라짐
        ZonedDateTime zdt = ts.toInstant().atZone(z);

        // ZonedDateTime을 원하는 패턴(p)으로 문자열 포맷팅해서 반환
        return zdt.format(DateTimeFormatter.ofPattern(p));
    }
}

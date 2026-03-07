package com.example.dueltower.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilityTest {

    @Test
    @DisplayName("MM 패턴은 월(month)을 출력하고 mm 패턴은 분(minute)을 출력한다")
    void monthAndMinutePatternShouldNotBeConfused() {
        Timestamp ts = Timestamp.from(Instant.parse("2025-03-15T10:07:00Z"));
        ZoneId zoneId = ZoneId.of("UTC");

        String monthFormatted = TimeUtility.formatTimestamp(ts, "yyyy-MM-dd", zoneId);
        String minuteFormatted = TimeUtility.formatTimestamp(ts, "yyyy-mm-dd", zoneId);

        assertEquals("2025-03-15", monthFormatted);
        assertEquals("2025-07-15", minuteFormatted);
    }

    @Test
    @DisplayName("Member에서 사용하는 yyyy-MM-dd 패턴이 월을 올바르게 표시한다")
    void yyyyUppercaseMMShouldFormatMonthCorrectly() {
        Timestamp ts = Timestamp.from(Instant.parse("2025-12-01T00:59:00Z"));

        String formatted = TimeUtility.formatTimestamp(ts, "yyyy-MM-dd", ZoneId.of("UTC"));

        assertEquals("2025-12-01", formatted);
    }
}

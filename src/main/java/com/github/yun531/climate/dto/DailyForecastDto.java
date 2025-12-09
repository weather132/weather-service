package com.github.yun531.climate.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일자별 AM/PM 예보 DTO
 * - 기준 날짜: reportTime.toLocalDate()
 * - 각 항목: dayOffset일 후의 AM/PM 온도/POP
 */
public record DailyForecastDto(
        int regionId,
        LocalDateTime reportTime,
        List<DailyForecastEntry> days
) {
    public record DailyForecastEntry(
            int dayOffset,   // 0=오늘, 1=내일...
            Integer amTemp,
            Integer pmTemp,
            Integer amPop,
            Integer pmPop
    ) {}
}
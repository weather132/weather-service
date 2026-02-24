package com.github.yun531.climate.notification.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RainForecastComputer 계산 결과 모델
 * - hourlyParts: 비가 오는 시간 구간 리스트 (validAt 기준)
 * - dayParts   : (dayOffset, amFlag, pmFlag) 리스트
 */
public record RainForecastParts(
        List<HourlyPart> hourlyParts,
        List<DayPart> dayParts
) {
    public RainForecastParts {
        hourlyParts = (hourlyParts == null) ? List.of() : List.copyOf(hourlyParts);
        dayParts    = (dayParts == null) ? List.of() : List.copyOf(dayParts);
    }

    /**
     * 시간대 비 구간
     * - start/end: 해당 구간의 "포인트 validAt" (end는 마지막 포함 포인트의 validAt)
     */
    public record HourlyPart(
            LocalDateTime start,
            LocalDateTime end
    ) {}

    /**
     * 일자별 비 플래그
     * - dayOffset: 0~6 (현재 기준 +N일)
     */
    public record DayPart(
            int dayOffset,
            boolean am,
            boolean pm
    ) {}
}
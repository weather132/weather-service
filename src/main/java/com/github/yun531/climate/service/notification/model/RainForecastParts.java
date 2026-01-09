package com.github.yun531.climate.service.notification.model;

import java.util.List;

/**
 * RainForecastComputer 계산 결과 모델
 * - hourlyParts: [startOffset, endOffset] 구간 리스트
 * - dayParts   : [amFlag, pmFlag] 리스트 (0/1)
 */
public record RainForecastParts(
        List<List<Integer>> hourlyParts,
        List<List<Integer>> dayParts
) {
    public RainForecastParts {
        hourlyParts = (hourlyParts == null) ? List.of() : List.copyOf(hourlyParts);
        dayParts    = (dayParts == null) ? List.of() : List.copyOf(dayParts);
    }
}
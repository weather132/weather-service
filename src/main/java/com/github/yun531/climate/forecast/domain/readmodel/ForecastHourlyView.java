package com.github.yun531.climate.forecast.domain.readmodel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시간대별 예보 DTO
 * - hourlyPoints: (validAt, temp, pop)
 */
public record ForecastHourlyView(
        String regionId,
        LocalDateTime reportTime,
        List<ForecastHourlyEntry> hourlyPoints
) {
    public ForecastHourlyView {
        hourlyPoints = (hourlyPoints == null) ? List.of() : List.copyOf(hourlyPoints);
    }
}
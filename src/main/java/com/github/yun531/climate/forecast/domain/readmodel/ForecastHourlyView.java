package com.github.yun531.climate.forecast.domain.readmodel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 시간대별 예보 DTO
 * - hourlyPoints: (validAt, temp, pop)
 */
public record ForecastHourlyView(
        String regionId,
        LocalDateTime reportTime,
        List<ForecastHourlyPoint> hourlyPoints
) {
    public ForecastHourlyView {
        Objects.requireNonNull(hourlyPoints, "hourlyPoints must not be null");
        hourlyPoints = List.copyOf(hourlyPoints);
    }
}
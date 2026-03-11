package com.github.yun531.climate.forecast.domain.readmodel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일자별 AM/PM 예보 DTO
 * - dailyPoints: dayOffset일 후의 AM/PM, 온도/POP
 */
public record ForecastDailyView(
        String regionId,
        LocalDateTime reportTime,
        List<ForecastDailyPoint> dailyPoints
) {
    public ForecastDailyView {
        dailyPoints = (dailyPoints == null) ? List.of() : List.copyOf(dailyPoints);
    }
}
package com.github.yun531.climate.snapshot.domain.readmodel;

import java.time.LocalDateTime;
import java.util.List;

public record WeatherSnapshot(
        String regionId,
        LocalDateTime announceTime,
        List<HourlyPoint> hourly,
        List<DailyPoint> daily
) {
    public WeatherSnapshot {
        hourly = (hourly == null) ? List.of() : List.copyOf(hourly);
        daily  = (daily  == null) ? List.of() : List.copyOf(daily);
    }
}
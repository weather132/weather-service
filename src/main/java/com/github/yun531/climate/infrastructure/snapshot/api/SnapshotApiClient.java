package com.github.yun531.climate.infrastructure.snapshot.api;

import com.github.yun531.climate.infrastructure.snapshot.dto.DailyForecastResponse;
import com.github.yun531.climate.infrastructure.snapshot.dto.HourlySnapshotResponse;

import java.time.LocalDateTime;

public interface SnapshotApiClient {
    HourlySnapshotResponse fetchHourly(String regionCode, LocalDateTime announceTime);
    DailyForecastResponse fetchDaily(String regionCode);
}
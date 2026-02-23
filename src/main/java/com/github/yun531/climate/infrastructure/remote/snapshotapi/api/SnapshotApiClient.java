package com.github.yun531.climate.infrastructure.remote.snapshotapi.api;

import com.github.yun531.climate.infrastructure.remote.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.infrastructure.remote.snapshotapi.dto.HourlySnapshotResponse;

import java.time.LocalDateTime;

public interface SnapshotApiClient {
    HourlySnapshotResponse fetchHourly(String regionCode, LocalDateTime announceTime);
    DailyForecastResponse fetchDaily(String regionCode);
}
package com.github.yun531.climate.snapshot.infra.remote.snapshotapi.api;

import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlyForecastResponse;

import java.time.LocalDateTime;

public interface SnapshotApiClient {
    HourlyForecastResponse fetchHourly(String regionCode, LocalDateTime announceTime);
    DailyForecastResponse fetchDaily(String regionCode);
}
package com.github.yun531.climate.infra.snapshotapi;

import com.github.yun531.climate.infra.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.infra.snapshotapi.dto.HourlySnapshotResponse;

import java.time.LocalDateTime;

public interface SnapshotApiClient {
    HourlySnapshotResponse fetchHourly(String regionCode, LocalDateTime announceTime);
    DailyForecastResponse fetchDaily(String regionCode);
}
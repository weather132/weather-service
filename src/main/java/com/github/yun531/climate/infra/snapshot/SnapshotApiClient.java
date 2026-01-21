package com.github.yun531.climate.infra.snapshot;

import com.github.yun531.climate.infra.snapshot.dto.DailyForecastResponse;
import com.github.yun531.climate.infra.snapshot.dto.HourlySnapshotResponse;

import java.time.LocalDateTime;

public interface SnapshotApiClient {
    HourlySnapshotResponse fetchHourly(String regionCode, LocalDateTime announceTime);
    DailyForecastResponse fetchDaily(String regionCode);
}
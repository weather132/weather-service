package com.github.yun531.climate.kernel.snapshot.readmodel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공통 인프라가 반환하는 "중립 스냅샷" 읽기모델.
 * - forecast/notification 어느 쪽에도 종속되지 않도록 shared에 위치.
 */
public record SnapshotForecast(
        String regionId,
        LocalDateTime reportTime,
        List<SnapshotHourlyPoint> hourly,
        List<SnapshotDailyPoint> daily
) {
    public SnapshotForecast {
        hourly = (hourly == null) ? List.of() : List.copyOf(hourly);
        daily  = (daily  == null) ? List.of() : List.copyOf(daily);
    }
}
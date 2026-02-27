package com.github.yun531.climate.service.query;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.port.SnapshotReadPort;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotDailyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.Snapshot;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotHourlyPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotQueryService {

    private static final SnapKind SNAP_CURRENT = SnapKind.CURRENT;

    private final SnapshotReadPort snapshotReadPort;

    /* ======================= 일반 일기예보 API용 (임시 유지) ======================= */

    /** 시간대별 온도+POP 예보 (현재 SNAP 기준) */
    public HourlyForecastDto getHourlyForecast(String regionId) {
        Snapshot snap = snapshotReadPort.load(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<SnapshotHourlyPoint> src = (snap.hourly() == null) ? List.of() : snap.hourly();

        // 중립 포인트 -> 기존 HourlyPoint로 임시 변환
        List<HourlyPoint> hours = src.stream()
                .filter(p -> p != null)
                .map(p -> new HourlyPoint(p.validAt(), p.temp(), p.pop()))
                .sorted(Comparator.comparing(
                        HourlyPoint::validAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        return new HourlyForecastDto(
                snap.regionId(),
                snap.reportTime(),
                hours
        );
    }

    /** 일자별 am/pm 온도+POP 예보 (현재 SNAP 기준) */
    public DailyForecastDto getDailyForecast(String regionId) {
        Snapshot snap = snapshotReadPort.load(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<SnapshotDailyPoint> src = (snap.daily() == null) ? List.of() : snap.daily();

        // 중립 포인트 -> 기존 DailyPoint로 임시 변환
        List<DailyPoint> days = src.stream()
                .filter(d -> d != null)
                .map(d -> new DailyPoint(
                        d.dayOffset(),
                        d.minTemp(),
                        d.maxTemp(),
                        d.amPop(),
                        d.pmPop()
                ))
                .sorted(Comparator.comparingInt(DailyPoint::dayOffset))
                .toList();

        return new DailyForecastDto(
                snap.regionId(),
                snap.reportTime(),
                days
        );
    }
}
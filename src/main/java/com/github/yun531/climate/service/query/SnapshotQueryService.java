package com.github.yun531.climate.service.query;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.kernel.snapshot.readmodel.WeatherSnapshot;
import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.reader.SnapshotReader;
import com.github.yun531.climate.kernel.snapshot.readmodel.DailyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.HourlyPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotQueryService {

    private static final SnapKind SNAP_CURRENT = SnapKind.CURRENT;

    private final SnapshotReader snapshotReader;

    /* ======================= 일반 일기예보 API용 (임시 유지) ======================= */

    /** 시간대별 온도+POP 예보 (현재 SNAP 기준) */
    public HourlyForecastDto getHourlyForecast(String regionId) {
        WeatherSnapshot snap = snapshotReader.load(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<HourlyPoint> src = (snap.hourly() == null) ? List.of() : snap.hourly();

        // 중립 포인트 -> 기존 HourlyPoint로 임시 변환
        List<com.github.yun531.climate.service.forecast.model.HourlyPoint> hours = src.stream()
                .filter(p -> p != null)
                .map(p -> new com.github.yun531.climate.service.forecast.model.HourlyPoint(p.validAt(), p.temp(), p.pop()))
                .sorted(Comparator.comparing(
                        com.github.yun531.climate.service.forecast.model.HourlyPoint::validAt,
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
        WeatherSnapshot snap = snapshotReader.load(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<DailyPoint> src = (snap.daily() == null) ? List.of() : snap.daily();

        // 중립 포인트 -> 기존 DailyPoint로 임시 변환
        List<com.github.yun531.climate.service.forecast.model.DailyPoint> days = src.stream()
                .filter(d -> d != null)
                .map(d -> new com.github.yun531.climate.service.forecast.model.DailyPoint(
                        d.dayOffset(),
                        d.minTemp(),
                        d.maxTemp(),
                        d.amPop(),
                        d.pmPop()
                ))
                .sorted(Comparator.comparingInt(com.github.yun531.climate.service.forecast.model.DailyPoint::dayOffset))
                .toList();

        return new DailyForecastDto(
                snap.regionId(),
                snap.reportTime(),
                days
        );
    }
}
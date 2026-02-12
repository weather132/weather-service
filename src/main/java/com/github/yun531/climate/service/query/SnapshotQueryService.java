package com.github.yun531.climate.service.query;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.notification.model.PopView;
import com.github.yun531.climate.service.notification.model.PopViewPair;
import com.github.yun531.climate.service.snapshot.SnapshotProvider;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**  스냅샷 로드 + PopView 변환 담당 */
@Service
@RequiredArgsConstructor
public class SnapshotQueryService {

    private static final int SNAP_CURRENT = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int SNAP_PREV    = SnapKindEnum.SNAP_PREVIOUS.getCode();

    private final SnapshotProvider snapshotProvider;

    /* ======================= 알림용 POP Projection ======================= */

    /** 기본 POP 시계열: (현재, 이전) SNAP */
    public PopViewPair loadDefaultPopViewPair(String regionId) {
        return loadPopViewPair(regionId, SNAP_CURRENT, SNAP_PREV);
    }

    /** 기본 POP 예보 요약: 현재 SNAP 기준 */
    public PopView loadDefaultPopView(String regionId) {
        return loadPopView(regionId, SNAP_CURRENT);
    }

    /** 비(POP) 판정에 필요한 시계열을 로드 (현재*이전 스냅샷) */
    public PopViewPair loadPopViewPair(String regionId, int currentSnapId, int previousSnapId) {
        ForecastSnap cur = snapshotProvider.loadSnapshot(regionId, currentSnapId);
        ForecastSnap prv = snapshotProvider.loadSnapshot(regionId, previousSnapId);

        if (cur == null || prv == null) return null;

        return new PopViewPair(
                PopView.fromSnap(cur),
                PopView.fromSnap(prv)
        );
    }

    /** 예보 요약용: 시간대 POP + 일자별 POP */
    public PopView loadPopView(String regionId, int snapId) {
        ForecastSnap snap = snapshotProvider.loadSnapshot(regionId, snapId);
        return (snap == null) ? null : PopView.fromSnap(snap);
    }

    /* ======================= 일반 일기예보 API용 ======================= */

    /** 시간대별 온도+POP 예보 (현재 SNAP 기준) */
    public HourlyForecastDto getHourlyForecast(String regionId) {
        ForecastSnap snap = snapshotProvider.loadSnapshot(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<HourlyPoint> src = (snap.hourly() == null) ? List.of() : snap.hourly();

        List<HourlyPoint> hours =
                src.stream()
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
        ForecastSnap snap = snapshotProvider.loadSnapshot(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<DailyPoint> src = (snap.daily() == null) ? List.of() : snap.daily();

        List<DailyPoint> days =
                src.stream()
                        .sorted(Comparator.comparingInt(DailyPoint::dayOffset))
                        .map(d -> new DailyPoint(
                                d.dayOffset(),
                                d.minTemp(),
                                d.maxTemp(),
                                d.amPop(),
                                d.pmPop()
                        ))
                        .toList();

        return new DailyForecastDto(
                snap.regionId(),
                snap.reportTime(),
                days
        );
    }
}
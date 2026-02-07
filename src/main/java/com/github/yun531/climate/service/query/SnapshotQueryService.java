package com.github.yun531.climate.service.query;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.notification.model.PopSeriesPair;
import com.github.yun531.climate.service.snapshot.SnapshotProvider;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 예보/알림 도메인 서비스
 * - POP 기반 알림용 시계열 생성 (PopSeriesPair, PopForecastSeries)
 * - 일반 일기예보용 DTO 생성 (HourlyForecastDto, DailyForecastDto)
 */
@Service
@RequiredArgsConstructor
public class SnapshotQueryService {

    private static final int SNAP_CURRENT = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int SNAP_PREV    = SnapKindEnum.SNAP_PREVIOUS.getCode();

    /** 정렬기준 */
    private static final Comparator<LocalDateTime> NULLS_LAST_TIME =
            Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<HourlyPoint> BY_VALID_AT =
            Comparator.comparing(HourlyPoint::validAt, NULLS_LAST_TIME);
    private static final Comparator<DailyPoint> BY_DAY_OFFSET =
            Comparator.comparingInt(DailyPoint::dayOffset);

    private final SnapshotProvider snapshotProvider;

    /* ======================= 알림용 POP 시계열 ======================= */

    /** 기본 POP 시계열: (현재, 이전) SNAP */
    public PopSeriesPair loadDefaultPopSeries(String regionId) {
        return loadPopSeries(regionId, SNAP_CURRENT, SNAP_PREV);
    }

    /** 기본 POP 예보 요약: 현재 SNAP 기준 */
    public PopForecastSeries loadDefaultForecastSeries(String regionId) {
        return loadForecastSeries(regionId, SNAP_CURRENT);
    }

    /** 비(POP) 판정에 필요한 시계열을 로드 (현재*이전 스냅샷) */
    public PopSeriesPair loadPopSeries(String regionId, int currentSnapId, int previousSnapId) {
        ForecastSnap cur = snapshotProvider.loadSnapshot(regionId, currentSnapId);
        ForecastSnap prv = snapshotProvider.loadSnapshot(regionId, previousSnapId);

        if (cur == null || prv == null) return emptyPopSeries();

        int reportTimeGap = computeReportTimeGap(prv.reportTime(), cur.reportTime());

        PopSeries24 curHourly = toPopSeries24(cur);
        PopSeries24 prvHourly = toPopSeries24(prv);

        return new PopSeriesPair(
                curHourly,
                prvHourly,
                reportTimeGap,
                cur.reportTime()
        );
    }

    /** 예보 요약용: 시간대 POP + 일자별 POP */
    public PopForecastSeries loadForecastSeries(String regionId, int snapId) {
        ForecastSnap snap = snapshotProvider.loadSnapshot(regionId, snapId);
        if (snap == null) return emptyForecastSeries();

        return new PopForecastSeries(
                toPopSeries24(snap),
                toPopDailySeries7(snap)
        );
    }

    /* ======================= 일반 일기예보 API용 ======================= */

    /** 시간대별 온도+POP 예보 (현재 SNAP 기준) */
    public HourlyForecastDto getHourlyForecast(String regionId) {
        ForecastSnap snap = snapshotProvider.loadSnapshot(regionId, SNAP_CURRENT);
        if (snap == null) return null;

        List<HourlyPoint> hours = sortedHourly(snap);

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

        // 기존의 "동일 값 복사(map -> new DailyPoint)" 제거
        List<DailyPoint> days = sortedDaily(snap);

        return new DailyForecastDto(
                snap.regionId(),
                snap.reportTime(),
                days
        );
    }

    /* ======================= POP 추출 헬퍼 ======================= */

    private PopSeries24 toPopSeries24(ForecastSnap snap) {
        // validAt 기준 정렬 후 (validAt, pop) 포인트 26개로 생성
        List<PopSeries24.Point> points =
                sortedHourly(snap).stream()
                        .map(p -> new PopSeries24.Point(p.validAt(), n(p.pop())))
                        .toList();

        return new PopSeries24(points);
    }

    private PopDailySeries7 toPopDailySeries7(ForecastSnap snap) {
        List<PopDailySeries7.DailyPop> days =
                sortedDaily(snap).stream()
                        .map(d -> new PopDailySeries7.DailyPop(n(d.amPop()), n(d.pmPop())))
                        .toList();

        // PopDailySeries7 생성자에서 size==7 체크
        return new PopDailySeries7(days);
    }

    private List<HourlyPoint> sortedHourly(ForecastSnap snap) {
        List<HourlyPoint> src = snap.hourly() == null ? List.of() : snap.hourly();
        return src.stream().sorted(BY_VALID_AT).toList();
    }

    private List<DailyPoint> sortedDaily(ForecastSnap snap) {
        List<DailyPoint> src = snap.daily() == null ? List.of() : snap.daily();
        return src.stream().sorted(BY_DAY_OFFSET).toList();
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    /* ======================= 유틸 / 빈 값 ======================= */

    private int computeReportTimeGap(LocalDateTime previous, LocalDateTime current) {
        if (previous == null || current == null) return 0;

        long minutes = Duration.between(previous, current).toMinutes();
        return (int) Math.round(minutes / 60.0);
    }

    private PopSeriesPair emptyPopSeries() {
        return new PopSeriesPair(null, null, 0, null);
    }

    private PopForecastSeries emptyForecastSeries() {
        return new PopForecastSeries(null, null);
    }
}
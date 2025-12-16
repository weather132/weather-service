package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.*;
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
public class ClimateService {

    private static final int SNAP_CURRENT = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int SNAP_PREV    = SnapKindEnum.SNAP_PREVIOUS.getCode();

    private final SnapshotProvider snapshotProvider;

    /* ======================= 알림용 POP 시계열 ======================= */

    /** 기본 POP 시계열: (현재, 이전) SNAP */
    public PopSeriesPair loadDefaultPopSeries(int regionId) {
        return loadPopSeries(regionId, SNAP_CURRENT, SNAP_PREV);
    }

    /** 기본 POP 예보 요약: 현재 SNAP 기준 */
    public PopForecastSeries loadDefaultForecastSeries(int regionId) {
        return loadForecastSeries(regionId, SNAP_CURRENT);
    }

    /** 비(POP) 판정에 필요한 시계열을 로드 (현재*이전 스냅샷) */
    public PopSeriesPair loadPopSeries(int regionId, int currentSnapId, int previousSnapId) {
        ForecastSnapshot cur = snapshotProvider.loadSnapshot(regionId, currentSnapId);
        ForecastSnapshot prv = snapshotProvider.loadSnapshot(regionId, previousSnapId);

        if (cur == null || prv == null) {
            return emptyPopSeries();
        }

        int reportTimeGap = computeReportTimeGap(prv.reportTime(), cur.reportTime());
        LocalDateTime curReportTime = cur.reportTime();

        PopSeries24 curHourly = toPopSeries24(cur);
        PopSeries24 prvHourly = toPopSeries24(prv);

        return new PopSeriesPair(
                curHourly,
                prvHourly,
                reportTimeGap,
                curReportTime
        );
    }

    /** 예보 요약용: 시간대 POP + 일자별 POP */
    public PopForecastSeries loadForecastSeries(int regionId, int snapId) {
        ForecastSnapshot snap = snapshotProvider.loadSnapshot(regionId, snapId);
        if (snap == null) {
            return emptyForecastSeries();
        }
        PopSeries24 hourly = toPopSeries24(snap);
        PopDailySeries7 daily = toPopDailySeries7(snap);
        return new PopForecastSeries(hourly, daily);
    }

    /* ======================= 일반 일기예보 API용 ======================= */

    /** 시간대별 온도+POP 예보 (현재 SNAP 기준) */
    public HourlyForecastDto getHourlyForecast(int regionId) {
        ForecastSnapshot snap = snapshotProvider.loadSnapshot(regionId, SNAP_CURRENT);
        if (snap == null) {
            return null; // 필요하면 Optional/예외로 바꿔도 됨
        }

        List<HourlyPoint> hours =
                snap.hourly().stream()
                        .sorted(Comparator.comparingInt(HourlyPoint::hourOffset))
                        .map(p -> new HourlyPoint(
                                p.hourOffset(),   // 몇 시간 후
                                p.temp(),
                                p.pop()
                        ))
                        .toList();

        return new HourlyForecastDto(
                snap.regionId(),
                snap.reportTime(),
                hours
        );
    }

    /** 일자별 am/pm 온도+POP 예보 (현재 SNAP 기준) */
    public DailyForecastDto getDailyForecast(int regionId) {
        ForecastSnapshot snap = snapshotProvider.loadSnapshot(regionId, SNAP_CURRENT);
        if (snap == null) {
            return null;
        }

        List<DailyPoint> days =
                snap.daily().stream()
                        .sorted(Comparator.comparingInt(DailyPoint::dayOffset))
                        .map(d -> new DailyPoint(
                                d.dayOffset(),
                                d.maxTemp(),
                                d.minTemp(),
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

    /* ======================= POP 추출 헬퍼 ======================= */

    private PopSeries24 toPopSeries24(ForecastSnapshot snap) {
        // hourOffset 기준 정렬 후 POP만 뽑아서 size 24 리스트 생성
        List<Integer> pops = snap.hourly().stream()
                .sorted(Comparator.comparingInt(HourlyPoint::hourOffset))
                .map(p -> n(p.pop()))
                .toList();
        return new PopSeries24(pops);
    }

    private PopDailySeries7 toPopDailySeries7(ForecastSnapshot snap) {
        // dayOffset 기준 정렬 후 DailyPop(AM/PM) 7개 생성
        List<PopDailySeries7.DailyPop> days = snap.daily().stream()
                .sorted(Comparator.comparingInt(DailyPoint::dayOffset))
                .map(d -> new PopDailySeries7.DailyPop(
                        n(d.amPop()),
                        n(d.pmPop())
                ))
                .toList();

        // PopDailySeries7 생성자에서 size==7 체크
        return new PopDailySeries7(days);
    }

    /** Integer → int 변환 + null → 0 */
    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    /* ======================= 유틸 / 빈 값 ======================= */

    private int computeReportTimeGap(LocalDateTime previous, LocalDateTime current) {
        if (previous == null || current == null) {
            return 0;
        }
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
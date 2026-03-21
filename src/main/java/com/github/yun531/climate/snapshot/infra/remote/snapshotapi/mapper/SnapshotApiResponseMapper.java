package com.github.yun531.climate.snapshot.infra.remote.snapshotapi.mapper;

import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.DailyForecastItem;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlyForecastItem;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlyForecastResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

/**
 * 외부 기상 API 응답(HourlySnapshotResponse, DailyForecastResponse)을
 * 내부 readmodel(WeatherSnapshot, HourlyPoint, DailyPoint)로 변환한다.
 */
@Component
public class SnapshotApiResponseMapper {

    private static final int MAX_HOURLY_POINTS = 26;
    private static final int DAILY_RANGE = 7;

    public WeatherSnapshot toSnapshot(
            String regionId,
            HourlyForecastResponse hourlyResponse,
            DailyForecastResponse dailyResponse,
            LocalDate baseDate
    ) {
        return new WeatherSnapshot(
                regionId,
                hourlyResponse.announceTime(),
                toHourlyPoints(hourlyResponse),
                toDailyPoints(dailyResponse, baseDate)
        );
    }

    // =====================================================================
    //  시간별: GridPoint 리스트 -> HourlyPoint 리스트 (최대 26개)
    // =====================================================================

    private List<HourlyPoint> toHourlyPoints(HourlyForecastResponse response) {
        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        HourlyForecastItem::effectiveTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .limit(MAX_HOURLY_POINTS)
                .map(p -> new HourlyPoint(p.effectiveTime(), p.temp(), p.pop()))
                .toList();
    }

    // =====================================================================
    //  일별: DailyForecastItem 리스트 -> DailyPoint 7개 (daysAhead 0~6)
    //
    //  각 offset별로:
    //    temp -> 구간 내 최소/최대
    //    pop  -> 오전(0~11시) / 오후(12~23시) 각각의 최대
    // =====================================================================

    private List<DailyPoint> toDailyPoints(DailyForecastResponse response, LocalDate baseDate) {
        if (baseDate == null || response == null || response.items() == null) {
            return emptyDailyPoints();
        }

        // 1) dayOffset별 그루핑
        DailyPoint[] points = new DailyPoint[DAILY_RANGE];
        Map<Integer, List<DailyForecastItem>> grouped = groupByDayOffset(baseDate, response.items());

        // 2) 각 offset별 집계
        for (int dayOffset = 0; dayOffset < DAILY_RANGE; dayOffset++) {
            List<DailyForecastItem> items = grouped.getOrDefault(dayOffset, List.of());
            points[dayOffset] = aggregateDay(dayOffset, items);
        }

        return List.of(points);
    }

    private Map<Integer, List<DailyForecastItem>> groupByDayOffset(
            LocalDate baseDate, List<DailyForecastItem> items
    ) {
        Map<Integer, List<DailyForecastItem>> grouped = new HashMap<>();

        for (DailyForecastItem item : items) {
            if (item == null || item.effectiveTime() == null) continue;

            int dayOffset = (int) ChronoUnit.DAYS.between(baseDate, item.effectiveTime().toLocalDate());
            if (dayOffset < 0 || dayOffset >= DAILY_RANGE) continue;

            grouped.computeIfAbsent(dayOffset, k -> new ArrayList<>()).add(item);
        }

        return grouped;
    }

    /**
     * 한 dayOffset에 속하는 item 들을 집계해 DailyPoint 하나를 만든다.
     * items가 비어있으면 모든 필드가 null인 DailyPoint를 반환한다.
     */
    private DailyPoint aggregateDay(int dayOffset, List<DailyForecastItem> items) {
        Integer minTemp = null, maxTemp = null;
        Integer amPop = null, pmPop = null;

        for (DailyForecastItem item : items) {
            // temp 누적
            Integer temp = item.temp();
            if (temp != null) {
                minTemp = (minTemp == null) ? temp : Math.min(minTemp, temp);
                maxTemp = (maxTemp == null) ? temp : Math.max(maxTemp, temp);
            }

            // pop 누적: 오전/오후 분리
            Integer pop = item.pop();
            if (pop != null) {
                if (item.effectiveTime().getHour() < 12) {
                    amPop = (amPop == null) ? pop : Math.max(amPop, pop);
                } else {
                    pmPop = (pmPop == null) ? pop : Math.max(pmPop, pop);
                }
            }
        }

        return new DailyPoint(dayOffset, minTemp, maxTemp, amPop, pmPop);
    }

    private List<DailyPoint> emptyDailyPoints() {
        return IntStream.range(0, DAILY_RANGE)
                .mapToObj(dayOffset -> new DailyPoint(dayOffset, null, null, null, null))
                .toList();
    }
}
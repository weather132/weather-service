package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.util.time.OffsetShiftUtil;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * HourlyForecastDto(시간대별 예보)를 now 기준으로 재표현.
 * - 스냅샷 3시간 갱신 가정: maxShiftHours=2 (0/1/2시간 재사용)
 * - diffHours 만큼 앞 요소 제거 + offset 1..N 재부여
 * - reportTime도 diffHours만큼 앞으로 보정
 */
public class HourlyForecastOffsetAdjuster {

    private final int maxShiftHours;

    public HourlyForecastOffsetAdjuster(int maxShiftHours) {
        this.maxShiftHours = maxShiftHours;
    }

    public HourlyForecastDto adjust(HourlyForecastDto base, LocalDateTime now) {
        if (base == null || base.reportTime() == null || base.hours() == null || base.hours().isEmpty()) {
            return base;
        }

        OffsetShiftUtil.OffsetShift shift =
                OffsetShiftUtil.compute(base.reportTime(), now, maxShiftHours);

        if (shift.diffHours() <= 0) {
            return base;
        }

        int diffHours = shift.diffHours();

        List<HourlyPoint> trimmed =
                base.hours().stream()
                        .sorted(Comparator.comparingInt(HourlyPoint::hourOffset))
                        .skip(diffHours)
                        .limit(24)
                        .toList();

        List<HourlyPoint> reindexed =
                IntStream.range(0, trimmed.size())
                        .mapToObj(i -> {
                            HourlyPoint p = trimmed.get(i);
                            return new HourlyPoint(i + 1, p.temp(), p.pop());
                        })
                        .toList();

        return new HourlyForecastDto(
                base.regionId(),
                shift.shiftedBaseTime(),
                reindexed
        );
    }
}
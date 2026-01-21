package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.util.time.TimeShiftUtil;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * HourlyForecastDto(시간대별 예보)를 now 기준으로 재표현.
 * - 스냅샷 3시간 갱신 가정: maxShiftHours=2 (0/1/2시간 재사용)
 * - reportTime(기준시각)을 now에 맞춰 shiftedBaseTime 으로 보정
 * - hourly는 "validAt(발효시각)" 기준으로, shiftedBaseTime 이후의 포인트 중 최대 24개로 절단
 */
public class HourlyForecastWindowAdjuster {

    private static final int WINDOW_SIZE = 24;

    private final int maxShiftHours;

    public HourlyForecastWindowAdjuster(int maxShiftHours) {
        this.maxShiftHours = maxShiftHours;
    }

    public HourlyForecastDto adjust(HourlyForecastDto base, LocalDateTime now) {
        if (base == null || base.reportTime() == null || base.hours() == null || base.hours().isEmpty()) {
            return base;
        }

        TimeShiftUtil.Shift shift =
                TimeShiftUtil.computeShift(base.reportTime(), now, maxShiftHours);

        // 0시간이면 "정렬만 보장"하고 그대로 반환해도 됨
        if (shift.diffHours() <= 0) {
            List<HourlyPoint> sorted =
                    base.hours().stream()
                            .sorted(Comparator.comparing(
                                    HourlyPoint::validAt,
                                    Comparator.nullsLast(Comparator.naturalOrder())
                            ))
                            .toList();

            return new HourlyForecastDto(base.regionId(), base.reportTime(), sorted);
        }

        LocalDateTime shiftedBaseTime = shift.shiftedBaseTime();

        // validAt 있는 포인트만 정렬 후, shiftedBaseTime 이후만 남김
        List<HourlyPoint> withValidAt =
                base.hours().stream()
                        .filter(p -> p != null && p.validAt() != null)
                        .sorted(Comparator.comparing(HourlyPoint::validAt))
                        .filter(p -> p.validAt().isAfter(shiftedBaseTime))
                        .limit(WINDOW_SIZE)
                        .toList();

        return new HourlyForecastDto(
                base.regionId(),
                shiftedBaseTime,
                withValidAt
        );
    }
}
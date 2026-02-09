package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.util.time.TimeShiftUtil;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * HourlyForecastDto(시간대별 예보)를 now 기준으로 재조정
 * - 스냅샷 3시간 갱신 가정: maxShiftHours=2 (0/1/2시간 재사용)
 * - reportTime(기준시각)을 now에 맞춰 shiftedBaseTime 으로 보정
 * - hourly는 validAt 기준 정렬 후, shiftedBaseTime 이상(>=)인 포인트 중 최대 24개로 절단
 */
public final class HourlyForecastWindowAdjuster {

    private static final int WINDOW_SIZE = 24;

    private static final Comparator<LocalDateTime> NULLS_LAST_TIME =
            Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<HourlyPoint> BY_VALID_AT =
            Comparator.comparing(HourlyPoint::validAt, NULLS_LAST_TIME);

    private final int maxShiftHours;

    public HourlyForecastWindowAdjuster(int maxShiftHours) {
        this.maxShiftHours = maxShiftHours;
    }

    /** 기준이 되는 HourlyForecastDto를 now 시각 기준으로 재조정 */
    public HourlyForecastDto adjust(HourlyForecastDto base, LocalDateTime now) {
        if (base == null) return null;

        List<HourlyPoint> sorted = normalize(base.hours());
        if (base.reportTime() == null || sorted.isEmpty() || now == null) {
            // 정렬만 보장한 형태로 반환
            return dto(base, base.reportTime(), sorted);
        }

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(base.reportTime(), now, maxShiftHours);
        if (shift.diffHours() <= 0) {
            return dto(base, base.reportTime(), sorted);
        }

        LocalDateTime shiftedBaseTime = shift.shiftedBaseTime();

        // shiftedBaseTime 이상(>=)인 포인트만 남기고 24개
        List<HourlyPoint> window =
                sorted.stream()
                        .filter(p -> p != null && p.validAt() != null)
                        .filter(p -> !p.validAt().isBefore(shiftedBaseTime)) // inclusive
                        .limit(WINDOW_SIZE)
                        .toList();

        return new HourlyForecastDto(base.regionId(), shiftedBaseTime, window);
    }

    private HourlyForecastDto dto(HourlyForecastDto base, LocalDateTime reportTime, List<HourlyPoint> hours) {
        return new HourlyForecastDto(base.regionId(), reportTime, hours);
    }

    private List<HourlyPoint> normalize(List<HourlyPoint> src) {
        if (src == null || src.isEmpty()) return List.of();
        return src.stream()
                .filter(Objects::nonNull)  // 안전
                .sorted(BY_VALID_AT)
                .toList();
    }
}
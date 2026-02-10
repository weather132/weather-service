package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.util.time.TimeShiftUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private static final Comparator<LocalDateTime> NULLS_LAST_TIME =
            Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<HourlyPoint> BY_VALID_AT =
            Comparator.comparing(HourlyPoint::validAt, NULLS_LAST_TIME);

    private final int maxShiftHours;
    private final int windowSize;

    public HourlyForecastWindowAdjuster(int maxShiftHours, int windowSize) {
        if (maxShiftHours < 0) throw new IllegalArgumentException("maxShiftHours must be >= 0");
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be > 0");
        this.maxShiftHours = maxShiftHours;
        this.windowSize = windowSize;
    }

    /** 기준이 되는 HourlyForecastDto를 now 시각 기준으로 재조정 */
    public HourlyForecastDto adjust(HourlyForecastDto base, LocalDateTime now) {
        if (base == null) return null;

        // validAt 기준 정렬(안전하게 null 제거)
        List<HourlyPoint> sorted = normalize(base.hours());

        LocalDateTime reportTime = base.reportTime();
        if (reportTime == null || now == null || sorted.isEmpty()) {
            // 정렬만 보장한 형태로 반환
            return new HourlyForecastDto(base.regionId(), reportTime, sorted);
        }

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(reportTime, now, maxShiftHours);
        if (shift.diffHours() <= 0) {
            return new HourlyForecastDto(base.regionId(), reportTime, sorted);
        }

        LocalDateTime shiftedBaseTime = shift.shiftedBaseTime();

        // shiftedBaseTime 이상(>=)인 포인트만 남기고 windowSize개
        List<HourlyPoint> window = buildWindow(sorted, shiftedBaseTime);

        return new HourlyForecastDto(base.regionId(), shiftedBaseTime, window);
    }

    /** shiftedBaseTime 초과(>)인 포인트만 남기고 windowSize 개로 절단 */
    private List<HourlyPoint> buildWindow(List<HourlyPoint> sorted, LocalDateTime shiftedBaseTime) {
        ArrayList<HourlyPoint> out = new ArrayList<>(Math.min(windowSize, 64));

        for (HourlyPoint p : sorted) {
            if (p == null) continue;

            LocalDateTime t = p.validAt();
            if (t == null) continue;

            // shiftedBaseTime 초과(>)만 포함 (exclusive)
            if (!t.isAfter(shiftedBaseTime)) continue;

            out.add(p);
            if (out.size() == windowSize) break;
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /** validAt 기준 정렬 + null 제거 */
    private List<HourlyPoint> normalize(List<HourlyPoint> src) {
        if (src == null || src.isEmpty()) return List.of();
        return src.stream()
                .filter(Objects::nonNull)
                .sorted(BY_VALID_AT)
                .toList();
    }
}
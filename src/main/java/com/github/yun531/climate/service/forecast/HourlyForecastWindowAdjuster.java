package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.shared.time.TimeShiftUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * ForecastConfig(@Bean)에서 생성되는 서비스.
 * HourlyForecastDto(시간대별 예보)를 now 기준으로 “윈도우” 형태로 재구성
 * - 스냅샷이 3시간 주기로 갱신된다는 가정 하에, reportTime 기준 최대 maxShiftHours(기본 2시간)까지 재사용
 * - reportTime과 now의 차이를 계산해 shiftedBaseTime(보정 기준시각)을 산출
 * - hourly 포인트는 validAt 오름차순 정렬 후, validAt이 기준시각(baseTime)보다 “이후(>)”인 것만 남김
 * - 남은 포인트를 windowSize 개로 제한하여 반환
 * - reportTime(또는 shiftedBaseTime)을 baseTime 으로 사용해 새 HourlyForecastDto를 생성
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
            return new HourlyForecastDto(base.regionId(), reportTime, sorted);
        }

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(reportTime, now, maxShiftHours);

        // 기준시각(baseTime) 초과(>)만 포함 + windowSize개 절단
        LocalDateTime baseTime = (shift.diffHours() <= 0) ? reportTime : shift.shiftedBaseTime();
        List<HourlyPoint> window = buildWindow(sorted, baseTime);

        return new HourlyForecastDto(base.regionId(), baseTime, window);
    }

    /** 기준시각(baseTime) 초과(>)인 포인트만 남기고 windowSize 개로 절단 */
    private List<HourlyPoint> buildWindow(List<HourlyPoint> sorted, LocalDateTime baseTime) {
        ArrayList<HourlyPoint> out = new ArrayList<>(windowSize);

        for (HourlyPoint p : sorted) {
            if (p == null) continue;

            LocalDateTime t = p.validAt();
            if (t == null) continue;

            // baseTime 초과(>)만 포함 (exclusive)
            if (!t.isAfter(baseTime)) continue;

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
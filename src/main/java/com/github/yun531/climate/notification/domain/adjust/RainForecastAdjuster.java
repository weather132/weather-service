package com.github.yun531.climate.notification.domain.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.RainInterval;
import com.github.yun531.climate.shared.time.TimeShiftUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.HOURS;

/**
 * RainForecast AlertEvent의 시간 시프트 + 윈도우 클리핑.
 * - occurredAt: 발표시각을 now 기준으로 최대 maxShiftHours만 큼 시프트
 * - hourlyParts: [nowHour + startOffset, nowHour + horizon] 윈도우로 클리핑
 * - dayParts: 날짜 경계를 넘으면 dayShift 만큼 앞쪽 드롭
 * 전제: 스냅샷 hourly 크기(26) = horizon(24) + maxShiftHours(2).
 * maxShiftHours 범위 내에서 항상 horizon 개의 데이터가 보장된다.
 * maxShiftHours 초과 시에는 상위 캐싱 레이어가 새 스냅샷을 로드한다.
 */
public class RainForecastAdjuster {

    private static final DailyRainFlags EMPTY_FLAGS = new DailyRainFlags(false, false);

    private final int maxShiftHours;
    private final int windowHours;
    private final int startOffsetHours;

    public RainForecastAdjuster(int maxShiftHours, int windowHours, int startOffsetHours) {
        this.maxShiftHours = Math.max(0, maxShiftHours);
        this.windowHours = Math.max(1, windowHours);
        this.startOffsetHours = Math.max(0, startOffsetHours);
    }

    /** 발표시각 기준 시프트 + 윈도우 클리핑을 적용한 AlertEvent를 반환 */
    public AlertEvent adjust(AlertEvent event, @Nullable LocalDateTime announceTime, LocalDateTime now) {
        if (event == null) return null;
        if (announceTime == null || now == null) return event;

        TimeShiftUtil.ShiftResult shift = TimeShiftUtil.shiftHourly(announceTime, now, maxShiftHours);

        if (!(event.payload() instanceof RainForecastPayload payload)) {
            return withShiftedTime(event, shift.shiftedBaseTime());
        }

        LocalDateTime nowHour = now.truncatedTo(HOURS);
        List<RainInterval> clamped = clampToWindow(
                payload.hourlyParts(),
                nowHour.plusHours(startOffsetHours),
                nowHour.plusHours(windowHours));

        List<DailyRainFlags> newDays = shiftDayParts(payload.dayParts(), shift.dayShift());

        RainForecastPayload newPayload = new RainForecastPayload(payload.type(), clamped, newDays);
        return withShiftedTime(event, shift.shiftedBaseTime(), newPayload);
    }

    // =====================================================================
    //  hourlyParts 윈도우 클리핑: 밖이면 제거, 걸치면 경계로 잘라서 보존
    // =====================================================================

    private List<RainInterval> clampToWindow(
            List<RainInterval> parts, LocalDateTime start, LocalDateTime end
    ) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<RainInterval> out = new ArrayList<>(parts.size());
        for (RainInterval r : parts) {
            RainInterval clamped = clampInterval(r, start, end);
            if (clamped != null) out.add(clamped);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    @Nullable
    private RainInterval clampInterval(RainInterval r, LocalDateTime start, LocalDateTime end) {
        if (r == null || r.start() == null || r.end() == null) return null;
        if (r.end().isBefore(start) || r.start().isAfter(end)) return null;

        LocalDateTime cStart = r.start().isBefore(start) ? start : r.start();
        LocalDateTime cEnd = r.end().isAfter(end) ? end : r.end();
        return cEnd.isBefore(cStart) ? null : new RainInterval(cStart, cEnd);
    }

    // =====================================================================
    //  dayParts 시프트: 앞쪽 드롭 + 뒤쪽 빈 플래그 패딩
    // =====================================================================

    private List<DailyRainFlags> shiftDayParts(List<DailyRainFlags> days, int dayShift) {
        if (days == null || days.isEmpty()) return List.of();
        if (dayShift <= 0) return days;

        int n = days.size();
        List<DailyRainFlags> out = new ArrayList<>(n);

        for (int i = dayShift; i < n; i++) {
            DailyRainFlags v = days.get(i);
            out.add(v != null ? v : EMPTY_FLAGS);
        }
        while (out.size() < n) {
            out.add(EMPTY_FLAGS);
        }

        return List.copyOf(out);
    }

    // =====================================================================
    //  AlertEvent 재조립 헬퍼
    // =====================================================================

    private AlertEvent withShiftedTime(AlertEvent event, LocalDateTime shiftedTime) {
        return new AlertEvent(event.type(), event.regionId(), shiftedTime, event.payload());
    }

    private AlertEvent withShiftedTime(AlertEvent event, LocalDateTime shiftedTime, RainForecastPayload payload) {
        return new AlertEvent(event.type(), event.regionId(), shiftedTime, payload);
    }
}
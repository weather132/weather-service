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
 * - baseTime을 now 기준으로 diffHours(<=maxShiftHours) 시프트
 * - hourlyParts를 window로 클리핑
 * - dayParts는 날짜 경계가 넘어가면 dayShift 만큼 드롭
 */
public class RainForecastAdjuster {

    private final int maxShiftHours;
    private final int horizonHours;
    private final int startOffsetHours;

    public RainForecastAdjuster(int maxShiftHours, int horizonHours) {
        this(maxShiftHours, horizonHours, 1);
    }

    public RainForecastAdjuster(int maxShiftHours, int horizonHours, int startOffsetHours) {
        this.maxShiftHours = Math.max(0, maxShiftHours);        // 0/1/2
        this.horizonHours = Math.max(1, horizonHours);          // 보통 24
        this.startOffsetHours = Math.max(0, startOffsetHours);  // 보통 1 (now+1부터)
    }

    /**
     * @param event    RainForecast AlertEvent
     * @param baseTime 데이터 발표시각 (시프트 기준)
     * @param now      현재 시각
     * @return 시간 보정된 AlertEvent
     */
    public AlertEvent adjust(AlertEvent event, @Nullable LocalDateTime baseTime, LocalDateTime now) {
        if (event == null) return null;
        if (baseTime == null || now == null) return event;

        TimeShiftUtil.ShiftResult shift = TimeShiftUtil.shiftHourly(baseTime, now, maxShiftHours);

        int diffHours = Math.max(0, shift.shiftHours());
        int dayShift  = Math.max(0, shift.dayShift());
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        LocalDateTime nowHour = now.truncatedTo(HOURS);
        LocalDateTime windowStart = nowHour.plusHours(diffHours + (long) startOffsetHours);
        LocalDateTime windowEnd   = nowHour.plusHours(diffHours + (long) horizonHours);

        if (!(event.payload() instanceof RainForecastPayload p)) {
            // 타입이 다르면 occurredAt만 보정
            return new AlertEvent(event.type(), event.regionId(), shiftedTime, event.payload());
        }

        List<RainInterval> clamped = clampToWindow(p.hourlyParts(), windowStart, windowEnd);
        List<DailyRainFlags> newDays = (dayShift > 0) ? shiftDayParts(p.dayParts(), dayShift) : p.dayParts();

        RainForecastPayload newPayload = new RainForecastPayload(p.type(), clamped, newDays);
        return new AlertEvent(event.type(), event.regionId(), shiftedTime, newPayload);
    }

    // -- hourlyParts 윈도우 클리핑 --
    // - - 완전히 밖이면 제거, 걸치면 경계로 잘라서 보존

    private List<RainInterval> clampToWindow(
            List<RainInterval> parts, LocalDateTime windowStart, LocalDateTime windowEnd
    ) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<RainInterval> out = new ArrayList<>();
        for (RainInterval r : parts) {
            if (r == null || r.start() == null || r.end() == null) continue;
            if (r.end().isBefore(windowStart) || r.start().isAfter(windowEnd)) continue;

            LocalDateTime clampedStart = r.start().isBefore(windowStart) ? windowStart : r.start();
            LocalDateTime clampedEnd = r.end().isAfter(windowEnd) ? windowEnd : r.end();

            if (!clampedEnd.isBefore(clampedStart)) {
                out.add(new RainInterval(clampedStart, clampedEnd));
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    // -- dayParts 시프트 --

    private List<DailyRainFlags> shiftDayParts(List<DailyRainFlags> days, int dayShift) {
        if (days == null || days.isEmpty() || dayShift <= 0) return (days == null) ? List.of() : days;

        int n = days.size();
        if (dayShift >= n) {
            List<DailyRainFlags> allFalse = new ArrayList<>(n);
            for (int i = 0; i < n; i++) allFalse.add(new DailyRainFlags(false, false));
            return List.copyOf(allFalse);
        }

        List<DailyRainFlags> out = new ArrayList<>(n);
        for (int i = dayShift; i < n; i++) {
            DailyRainFlags v = days.get(i);
            out.add(v == null ? new DailyRainFlags(false, false) : v);
        }
        for (int i = 0; i < dayShift; i++) {
            out.add(new DailyRainFlags(false, false));
        }
        return List.copyOf(out);
    }
}
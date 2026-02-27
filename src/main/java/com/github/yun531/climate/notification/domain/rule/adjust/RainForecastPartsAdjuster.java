package com.github.yun531.climate.notification.domain.rule.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.payload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainInterval;
import com.github.yun531.climate.shared.time.TimeShiftUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.HOURS;

public class RainForecastPartsAdjuster {

    private final int maxShiftHours;    // 0/1/2
    private final int horizonHours;     // 보통 24
    private final int startOffsetHours; // 보통 1 (now+1부터)

    public RainForecastPartsAdjuster(int maxShiftHours, int horizonHours) {
        this(maxShiftHours, horizonHours, 1);
    }

    public RainForecastPartsAdjuster(int maxShiftHours, int horizonHours, int startOffsetHours) {
        this.maxShiftHours = Math.max(0, maxShiftHours);
        this.horizonHours = Math.max(1, horizonHours);
        this.startOffsetHours = Math.max(0, startOffsetHours);
    }

    /**
     * - baseTime을 now 기준으로 diffHours(<=maxShiftHours) 시프트
     * - event.occurredAt을 shiftedBaseTime 으로 보정 (ui에 업데이트 시간을 띄워주기 위함)
     * - hourlyParts를 window로 클리핑
     *   windowStart = nowHour + (diffHours + startOffsetHours)
     *   windowEnd = nowHour + (diffHours + horizonHours)
     * - dayParts는 날짜 경계가 넘어가면 dayShift 만큼 드롭하고 뒤를 false로 채움(7개 유지 전제)
     */
    public AlertEvent adjust(AlertEvent event, @Nullable LocalDateTime baseTime, LocalDateTime now) {
        if (event == null) return null;
        if (baseTime == null || now == null) return event;

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(baseTime, now, maxShiftHours);

        int diffHours = Math.max(0, shift.diffHours());
        int dayShift  = Math.max(0, shift.dayShift());
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        LocalDateTime nowHour = now.truncatedTo(HOURS);
        LocalDateTime windowStart = nowHour.plusHours(diffHours + (long) startOffsetHours);
        LocalDateTime windowEnd   = nowHour.plusHours(diffHours + (long) horizonHours);

        if (!(event.payload() instanceof RainForecastPayload p)) {
            // 타입이 다르면 occurredAt만 보정(통과)
            return new AlertEvent(event.type(), event.regionId(), shiftedTime, event.payload());
        }

        // 내부 계산은 LocalDateTime 기반(TimeRange)으로
        List<RainInterval> clamped = clampHourlyPartsToWindow(p.hourlyParts(), windowStart, windowEnd);
        List<DailyRainFlags> newDays = (dayShift > 0) ? shiftDayParts(p.dayParts(), dayShift) : p.dayParts();

        RainForecastPayload newPayload = new RainForecastPayload(p.srcRule(), clamped, newDays);
        return new AlertEvent(event.type(), event.regionId(), shiftedTime, newPayload);
    }

    /**
     * hourlyParts: [startValidAt, endValidAt] 리스트를 window로 클리핑
     * - 완전히 밖이면 제거
     * - 걸치면 경계로 잘라서 보존
     * - end는 포함(inclusive)로 취급
     */
    private List<RainInterval> clampHourlyPartsToWindow(List<RainInterval> parts,
                                                        LocalDateTime windowStart,
                                                        LocalDateTime windowEnd) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<RainInterval> out = new ArrayList<>();
        for (RainInterval r : parts) {
            if (r == null) continue;

            RainInterval c = r.clamp(windowStart, windowEnd);

            // window 밖이면 제거, 걸치면 경계로 잘라서 보존
            // end는 inclusive 취급 (TimeRange.clamp 구현 정책에 맞춤)
            if (c != null) out.add(c);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /**
     * dayParts: 날짜가 넘어가면 dayShift 만큼 앞에서 drop, 뒤를 false로 채움
     * - 항상 7개 유지(RainForecastPayload가 정규화해준다는 전제)
     */
    private List<DailyRainFlags> shiftDayParts(List<DailyRainFlags> days, int dayShift) {
        if (days == null || days.isEmpty() || dayShift <= 0) return (days == null) ? List.of() : days;

        int n = days.size();
        if (dayShift >= n) {
            // 전부 밀려나면 전부 false로 채움
            List<DailyRainFlags> allFalse = new ArrayList<>(n);
            for (int i = 0; i < n; i++) allFalse.add(new DailyRainFlags(false, false));
            return List.copyOf(allFalse);
        }

        List<DailyRainFlags> out = new ArrayList<>(n);

        // 앞에서 dayShift 만큼 drop
        for (int i = dayShift; i < n; i++) {
            DailyRainFlags v = days.get(i);
            out.add(v == null ? new DailyRainFlags(false, false) : v);
        }

        // 뒤를 false로 채움
        for (int i = 0; i < dayShift; i++) out.add(new DailyRainFlags(false, false));

        return List.copyOf(out);
    }
}
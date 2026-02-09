package com.github.yun531.climate.service.notification.rule.adjust;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.payload.DailyRainFlags;
import com.github.yun531.climate.service.notification.model.payload.RainForecastPayload;
import com.github.yun531.climate.service.notification.model.payload.RainInterval;
import com.github.yun531.climate.util.time.TimeShiftUtil;
import io.micrometer.common.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.HOURS;

public class RainForecastPartsAdjuster {

    private final int maxShiftHours;
    private final int maxHourlyHours;

    public RainForecastPartsAdjuster(int maxShiftHours, int maxHourlyHours) {
        this.maxShiftHours = maxShiftHours;
        this.maxHourlyHours = maxHourlyHours;
    }

    /**
     * - baseTime(=entry.computedAt)를 now 기준으로 diffHours(<=maxShiftHours) 시프트
     * - event.occurredAt을 shiftedBaseTime 으로 보정
     * - hourlyParts: [startValidAt, endValidAt]를 now+diffHours 기준 "horizonHours(<=24) 영역"으로 클리핑
     *      windowStart = now(truncated) + (diffHours + 1)
     *      windowEnd   = now(truncated) + (diffHours + horizonHours)
     *   -> window와 겹치는 구간만 남기고, start/end는 window 경계로 자름
     * - dayParts: 날짜가 넘어가면 dayShift 만큼 앞에서 drop, 뒤를 false로 채움 (항상 7개 유지)
     */
    public List<AlertEvent> adjust(List<AlertEvent> events,
                                   @Nullable LocalDateTime baseTime,
                                   LocalDateTime now) {
        if (events == null || events.isEmpty()) return List.of();
        if (baseTime == null || now == null) return List.copyOf(events);

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(baseTime, now, maxShiftHours);

        int diffHours = Math.max(0, shift.diffHours()); // 0/1/2... (maxShiftHours 범위)
        int dayShift  = Math.max(0, shift.dayShift());
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        // 0 이하로 들어오면 결과가 전부 날아가므로 최소 1 보장
        int horizonHours = Math.max(1, Math.min(24, maxHourlyHours));

        LocalDateTime nowHour = now.truncatedTo(HOURS);
        LocalDateTime windowStart = nowHour.plusHours(diffHours + 1L);
        LocalDateTime windowEnd   = nowHour.plusHours(diffHours + (long) horizonHours);

        List<AlertEvent> out = new ArrayList<>(events.size());

        for (AlertEvent e : events) {
            if (!(e.payload() instanceof RainForecastPayload p)) {
                // 타입이 다르면 occurredAt만 보정하고 payload는 건드리지 않음(방어)
                out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, e.payload()));
                continue;
            }

            // 내부 계산은 LocalDateTime 기반(TimeRange)으로
            List<RainInterval> clamped = clampHourlyPartsToWindow(p.hourlyParts(), windowStart, windowEnd);
            List<DailyRainFlags> newDays  = (dayShift > 0) ? shiftDayParts(p.dayParts(), dayShift) : p.dayParts();

            RainForecastPayload newPayload =
                    new RainForecastPayload(p.srcRule(), clamped, newDays);

            out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, newPayload));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
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
        if (days == null || days.isEmpty() || dayShift <= 0) return days == null ? List.of() : days;

        int n = days.size();

        if (dayShift >= n) {
            // 전부 밀려나면 전부 false로 채움
            return List.of(
                    new DailyRainFlags(false, false),
                    new DailyRainFlags(false, false),
                    new DailyRainFlags(false, false),
                    new DailyRainFlags(false, false),
                    new DailyRainFlags(false, false),
                    new DailyRainFlags(false, false),
                    new DailyRainFlags(false, false)
            );
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
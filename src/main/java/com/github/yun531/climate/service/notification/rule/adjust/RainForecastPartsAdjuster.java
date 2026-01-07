package com.github.yun531.climate.service.notification.rule.adjust;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.util.time.OffsetShiftUtil;
import io.micrometer.common.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RainForecastPartsAdjuster {

    private final String hourlyPartsKey;
    private final String dayPartsKey;
    private final int maxShiftHours;
    private final int maxHourlyHours;

    public RainForecastPartsAdjuster(String hourlyPartsKey,
                                     String dayPartsKey,
                                     int maxShiftHours,
                                     int maxHourlyHours) {
        this.hourlyPartsKey = hourlyPartsKey;
        this.dayPartsKey = dayPartsKey;
        this.maxShiftHours = maxShiftHours;
        this.maxHourlyHours = maxHourlyHours;
    }

    /**
     * - baseTime(=entry.computedAt)를 now 기준으로 diffHours(<=2) 시프트
     * - event.occurredAt을 baseTime+diffHours로 보정
     * - hourlyParts: [s,e] -> [max(1,s-d), e-d], e-d<1 이면 제거
     * - dayParts: 날짜가 넘어가면 dayShift 만큼 앞에서 drop, 뒤를 [0,0]으로 채움
     */
    public List<AlertEvent> adjust(List<AlertEvent> events,
                                   @Nullable LocalDateTime baseTime,
                                   LocalDateTime now) {
        if (baseTime == null || events == null || events.isEmpty()) {
            return events == null ? List.of() : events;
        }

        OffsetShiftUtil.OffsetShift shift = OffsetShiftUtil.compute(baseTime, now, maxShiftHours);
        if (shift.diffHours() <= 0) {
            return events;
        }

        int diffHours = shift.diffHours();
        int dayShift = shift.dayShift();
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        List<AlertEvent> out = new ArrayList<>(events.size());

        for (AlertEvent e : events) {
            Map<String, Object> payload = e.payload();
            if (payload == null || payload.isEmpty()) {
                out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, payload));
                continue;
            }

            List<List<Integer>> hourlyParts = safeRead2dIntList(payload.get(hourlyPartsKey));
            List<List<Integer>> dayParts = safeRead2dIntList(payload.get(dayPartsKey));

            List<List<Integer>> newHourly = shiftHourlyParts(hourlyParts, diffHours);
            List<List<Integer>> newDays = (dayShift > 0) ? shiftDayParts(dayParts, dayShift) : dayParts;

            Map<String, Object> newPayload = new HashMap<>(payload);
            newPayload.put(hourlyPartsKey, newHourly);
            newPayload.put(dayPartsKey, newDays);

            out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, Map.copyOf(newPayload)));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private List<List<Integer>> shiftHourlyParts(List<List<Integer>> parts, int diffHours) {
        if (parts == null || parts.isEmpty() || diffHours <= 0) return parts == null ? List.of() : parts;

        List<List<Integer>> out = new ArrayList<>();

        for (List<Integer> p : parts) {
            if (p == null || p.size() < 2) continue;

            Integer sObj = p.get(0);
            Integer eObj = p.get(1);
            if (sObj == null || eObj == null) continue;

            int s = sObj - diffHours;
            int e = eObj - diffHours;

            if (e < 1) continue;
            if (s < 1) s = 1;
            if (s > maxHourlyHours) continue;

            if (e > maxHourlyHours) e = maxHourlyHours;
            if (e < s) continue;

            out.add(List.of(s, e));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private List<List<Integer>> shiftDayParts(List<List<Integer>> dayParts, int dayShift) {
        if (dayParts == null || dayParts.isEmpty() || dayShift <= 0) return dayParts == null ? List.of() : dayParts;

        int n = dayParts.size();

        if (dayShift >= n) {
            List<List<Integer>> zeros = new ArrayList<>(n);
            for (int i = 0; i < n; i++) zeros.add(List.of(0, 0));
            return List.copyOf(zeros);
        }

        List<List<Integer>> out = new ArrayList<>(n);

        for (int i = dayShift; i < n; i++) {
            List<Integer> row = dayParts.get(i);
            out.add((row == null || row.size() < 2) ? List.of(0, 0) : List.of(row.get(0), row.get(1)));
        }
        for (int i = 0; i < dayShift; i++) {
            out.add(List.of(0, 0));
        }

        return List.copyOf(out);
    }

    private List<List<Integer>> safeRead2dIntList(Object obj) {
        if (!(obj instanceof List<?> outer)) return List.of();

        List<List<Integer>> out = new ArrayList<>();
        for (Object rowObj : outer) {
            if (!(rowObj instanceof List<?> row) || row.size() < 2) continue;

            Integer a = toInt(row.get(0));
            Integer b = toInt(row.get(1));
            if (a == null || b == null) continue;

            out.add(List.of(a, b));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private Integer toInt(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        return null;
    }
}
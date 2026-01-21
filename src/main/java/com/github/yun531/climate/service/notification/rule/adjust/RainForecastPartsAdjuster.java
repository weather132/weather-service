package com.github.yun531.climate.service.notification.rule.adjust;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.util.time.TimeShiftUtil;
import io.micrometer.common.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.HOURS;

public class RainForecastPartsAdjuster {

    private static final Logger log = LoggerFactory.getLogger(RainForecastPartsAdjuster.class);

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
     * - event.occurredAt을 shiftedBaseTime 으로 보정
     * - hourlyParts: [startValidAt, endValidAt]를 now+diffHours 기준 "24시간 영역"으로 클리핑
     *      windowStart = now + (diffHours + 1)
     *      windowEnd   = now + (diffHours + 24)
     *   -> window와 겹치는 구간만 남기고, start/end는 window 경계로 자름
     * - dayParts: 날짜가 넘어가면 dayShift 만큼 앞에서 drop, 뒤를 [0,0]으로 채움 (기존 유지)
     */
    public List<AlertEvent> adjust(List<AlertEvent> events,
                                   @Nullable LocalDateTime baseTime,
                                   LocalDateTime now) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        if (baseTime == null) {
            return List.copyOf(events);
        }

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(baseTime, now, maxShiftHours);

        int diffHours = Math.max(0, shift.diffHours()); // 0/1/2
        int dayShift = Math.max(0, shift.dayShift());
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        // "24시간 영역"은 maxHourlyHours가 더 작으면 그만큼만(방어)
        int horizonHours = Math.min(24, maxHourlyHours);

        now = now.truncatedTo(HOURS);
        LocalDateTime windowStart = now.plusHours(diffHours + 1L);
        LocalDateTime windowEnd   = now.plusHours(diffHours + (long) horizonHours);

        List<AlertEvent> out = new ArrayList<>(events.size());

        for (AlertEvent e : events) {
            Map<String, Object> payload = e.payload();

            if (payload == null || payload.isEmpty()) {
                out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, payload));
                continue;
            }

            List<List<LocalDateTime>> hourlyParts = safeRead2dDateTimeList(payload.get(hourlyPartsKey));
            List<List<Integer>> dayParts = safeRead2dIntList(payload.get(dayPartsKey));

            List<List<LocalDateTime>> newHourly = clampHourlyPartsToWindow(hourlyParts, windowStart, windowEnd);
            List<List<Integer>> newDays = (dayShift > 0) ? shiftDayParts(dayParts, dayShift) : dayParts;

            Map<String, Object> newPayload = new HashMap<>(payload);
            newPayload.put(hourlyPartsKey, newHourly);
            newPayload.put(dayPartsKey, newDays);

            out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, Map.copyOf(newPayload)));
        }

        return List.copyOf(out);
    }

    /**
     * hourlyParts: [startValidAt, endValidAt] 리스트를 window로 클리핑
     * - 완전히 밖이면 제거
     * - 걸치면 경계로 잘라서 보존
     * - end는 포함(inclusive)로 취급
     */
    private List<List<LocalDateTime>> clampHourlyPartsToWindow(List<List<LocalDateTime>> parts,
                                                               LocalDateTime windowStart,
                                                               LocalDateTime windowEnd) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<List<LocalDateTime>> out = new ArrayList<>();

        for (List<LocalDateTime> p : parts) {
            if (p == null || p.size() < 2) continue;

            LocalDateTime s = p.get(0);
            LocalDateTime e = p.get(1);
            if (s == null || e == null) continue;

            // 구간 정규화(혹시 뒤집혀 들어와도 방어)
            LocalDateTime start = s.isBefore(e) ? s : e;
            LocalDateTime end   = s.isBefore(e) ? e : s;

            // window 밖이면 제거
            if (end.isBefore(windowStart)) continue;
            if (start.isAfter(windowEnd)) continue;

            // 클리핑
            LocalDateTime ns = start.isBefore(windowStart) ? windowStart : start;
            LocalDateTime ne = end.isAfter(windowEnd) ? windowEnd : end;

            if (ne.isBefore(ns)) continue;
            out.add(List.of(ns, ne));
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

    /**
     * payload[hourlyPartsKey]를 "2차원 LocalDateTime 리스트"로 읽기
     * - row: [startValidAt, endValidAt]
     * - 값은 LocalDateTime 만 허용
     */
    private List<List<LocalDateTime>> safeRead2dDateTimeList(Object obj) {
        if (!(obj instanceof List<?> outer)) return List.of();

        List<List<LocalDateTime>> out = new ArrayList<>();

        for (Object rowObj : outer) {
            if (!(rowObj instanceof List<?> row) || row.size() < 2) continue;

            Object aObj = row.get(0);
            Object bObj = row.get(1);

            if (!(aObj instanceof String aStr) || !(bObj instanceof String bStr)) {
                log.warn("hourlyParts row 타입 불일치: row={}, aType={}, aValue={}, bType={}, bValue={}",
                        row,
                        (aObj == null ? "null" : aObj.getClass().getName()), aObj,
                        (bObj == null ? "null" : bObj.getClass().getName()), bObj
                );
                continue;
            }

            try {
                LocalDateTime a = LocalDateTime.parse(aStr);
                LocalDateTime b = LocalDateTime.parse(bStr);
                out.add(List.of(a, b));
            } catch (DateTimeParseException ex) {
                // 포맷이 확정이라면 사실상 발생하면 데이터가 깨진 것
                log.warn("hourlyParts datetime 파싱 실패(ISO-8601 기대): row={}, start='{}', end='{}', reason={}",
                        row, aStr, bStr, ex.getMessage());
            }
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
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
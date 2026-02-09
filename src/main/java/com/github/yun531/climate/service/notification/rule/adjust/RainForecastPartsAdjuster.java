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
        if (events == null || events.isEmpty()) return List.of();
        if (baseTime == null) return List.copyOf(events);

        TimeShiftUtil.Shift shift = TimeShiftUtil.computeShift(baseTime, now, maxShiftHours);

        int diffHours = Math.max(0, shift.diffHours()); // 0/1/2
        int dayShift  = Math.max(0, shift.dayShift());
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        // 0 이하로 들어오면 결과가 전부 날아가므로 최소 1 보장
        int horizonHours = Math.max(1, Math.min(24, maxHourlyHours));

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

            // 입력은 String/LocalDateTime 모두 허용(캐시/이전버전 호환)
            List<List<LocalDateTime>> hourlyParts = safeRead2dDateTimeList(payload.get(hourlyPartsKey));
            List<List<Integer>> dayParts = safeRead2dIntList(payload.get(dayPartsKey));

            // 내부 계산은 LocalDateTime으로
            List<List<LocalDateTime>> clamped = clampHourlyPartsToWindow(hourlyParts, windowStart, windowEnd);
            List<List<Integer>> newDays = (dayShift > 0) ? shiftDayParts(dayParts, dayShift) : dayParts;

            // payload에는 "문자열(ISO)"로 고정해서 넣기
            List<List<String>> newHourlyIso = toIso2d(clamped);

            Map<String, Object> newPayload = new HashMap<>(payload);
            newPayload.put(hourlyPartsKey, newHourlyIso);
            newPayload.put(dayPartsKey, newDays);

            out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, Map.copyOf(newPayload)));
        }

        return List.copyOf(out);
    }

    private List<List<String>> toIso2d(List<List<LocalDateTime>> parts) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<List<String>> out = new ArrayList<>(parts.size());
        for (List<LocalDateTime> row : parts) {
            if (row == null || row.size() < 2) continue;

            LocalDateTime a = row.get(0);
            LocalDateTime b = row.get(1);
            if (a == null || b == null) continue;

            out.add(List.of(a.toString(), b.toString()));
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
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
        for (int i = 0; i < dayShift; i++) out.add(List.of(0, 0));

        return List.copyOf(out);
    }

    /**
     * payload[hourlyPartsKey]를 "2차원 LocalDateTime 리스트"로 읽기
     * - row: [start, end]
     * - 각 값은 String (ISO) 또는 LocalDateTime 허용
     */
    private List<List<LocalDateTime>> safeRead2dDateTimeList(Object obj) {
        if (!(obj instanceof List<?> outer)) return List.of();

        List<List<LocalDateTime>> out = new ArrayList<>();

        for (Object rowObj : outer) {
            if (!(rowObj instanceof List<?> row) || row.size() < 2) continue;

            LocalDateTime a = toLocalDateTime(row.get(0));
            LocalDateTime b = toLocalDateTime(row.get(1));
            if (a == null || b == null) continue;

            out.add(List.of(a, b));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private LocalDateTime toLocalDateTime(Object o) {
        if (o instanceof LocalDateTime t) return t;
        if (o instanceof String s) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException ex) {
                log.warn("hourlyParts datetime 파싱 실패(ISO 기대): value='{}', reason={}", s, ex.getMessage());
                return null;
            }
        }

        // 타입 불일치 로그(원인 추적용)
        if (o != null) {
            log.warn("hourlyParts 값 타입 불일치: type={}, value={}", o.getClass().getName(), o);
        }
        return null;
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
package com.github.yun531.climate.service.notification.rule.adjust;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import io.micrometer.common.lang.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * baseTime 없이 now 기준으로 validAt 윈도우(1~24h)만 남기는 Adjuster
 * - now는 truncatedTo(HOURS)로 정각 기준
 * - window: [now+1, now+24]
 * - payload[validAtKey] 는 String(ISO) 또는 LocalDateTime 허용
 * - 윈도우 밖 validAt 이벤트는 제거
 * - occurredAt은 now(정각)로 통일
 */
public class ValidAtEventAdjuster {

    private final String validAtKey;
    private final int windowHours; // 보통 24

    public ValidAtEventAdjuster(String validAtKey) {
        this(validAtKey, 24);
    }

    public ValidAtEventAdjuster(String validAtKey, int windowHours) {
        this.validAtKey = validAtKey;
        this.windowHours = windowHours;
    }

    public List<AlertEvent> adjust(List<AlertEvent> events, LocalDateTime now) {
        if (events == null || events.isEmpty()) return List.of();
        if (now == null) return List.copyOf(events);

        LocalDateTime nowHour = now.truncatedTo(ChronoUnit.HOURS);

        LocalDateTime windowStart = nowHour.plusHours(1L);
        LocalDateTime windowEnd   = nowHour.plusHours((long) windowHours);

        List<AlertEvent> kept = new ArrayList<>(events.size());

        for (AlertEvent e : events) {
            LocalDateTime validAt = readValidAt(e);

            // validAt이 없으면 윈도우 판정 불가 → 제거
            if (validAt == null) continue;

            // [start, end] 포함 범위로 취급
            if (validAt.isBefore(windowStart) || validAt.isAfter(windowEnd)) {
                continue;
            }

            // occurredAt은 nowHour로 통일, payload는 그대로(직렬화된 validAt 유지)
            kept.add(new AlertEvent(e.type(), e.regionId(), nowHour, e.payload()));
        }

        if (kept.isEmpty()) return List.of();

        // validAt 기준 정렬 후 최대 windowHours개 유지  중복/오염 방어)
        kept.sort(Comparator.comparing(this::readValidAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (kept.size() > windowHours) {
            kept = kept.subList(0, windowHours);
        }

        return List.copyOf(kept);
    }

    @Nullable
    private LocalDateTime readValidAt(AlertEvent e) {
        if (e == null) return null;

        Map<String, Object> payload = e.payload();
        if (payload == null) return null;

        Object v = payload.get(validAtKey);
        if (v instanceof LocalDateTime t) return t;

        if (v instanceof String s) {
            try {
                return LocalDateTime.parse(s); // ISO-8601 기대: "2026-01-14T21:00:00"
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        return null;
    }
}
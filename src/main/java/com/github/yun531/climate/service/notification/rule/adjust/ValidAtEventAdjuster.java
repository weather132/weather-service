package com.github.yun531.climate.service.notification.rule.adjust;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.payload.ValidAtPayload;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * baseTime 없이 now 기준으로 validAt 윈도우(1~24h)만 남기는 Adjuster
 * - now는 truncatedTo(HOURS)로 정각 기준
 * - window: [now+1, now+windowHours]
 * - payload는 typed payload(ValidAtPayload) 기반으로 validAt을 읽음
 * - 윈도우 밖 validAt 이벤트는 제거
 * - occurredAt은 now(정각)로 통일
 */
public class ValidAtEventAdjuster {

    private final int maxWindowHours;   // 기본 24
    private final int startOffsetHours; // 기본 1 (now+1부터)

    public ValidAtEventAdjuster(int maxWindowHours) {
        this(maxWindowHours, 1);
    }

    public ValidAtEventAdjuster(int maxWindowHours, int startOffsetHours) {
        this.maxWindowHours = Math.max(0, maxWindowHours);
        this.startOffsetHours = Math.max(0, startOffsetHours);
    }

    /** @param hourLimitInclusive now 기준 N시간 이내 제한(없으면 maxWindowHours 사용) */
    public List<AlertEvent> adjust(List<AlertEvent> events, LocalDateTime now, @Nullable Integer hourLimitInclusive) {
        if (events == null || events.isEmpty()) return List.of();
        if (now == null) return List.copyOf(events);

        LocalDateTime nowHour = now.truncatedTo(ChronoUnit.HOURS);

        int endOffset = computeEndOffset(hourLimitInclusive);
        if (endOffset < startOffsetHours) return List.of();

        LocalDateTime windowStart = nowHour.plusHours(startOffsetHours);
        LocalDateTime windowEnd   = nowHour.plusHours(endOffset);

        int cap = endOffset - startOffsetHours + 1;

        ArrayList<AlertEvent> kept = new ArrayList<>(Math.min(events.size(), cap));

        for (AlertEvent e : events) {
            LocalDateTime validAt = readValidAt(e);

            // validAt이 없으면 윈도우 판정 불가 → 제거
            if (validAt == null) continue;

            // [start, end] 포함 범위로 취급
            if (validAt.isBefore(windowStart) || validAt.isAfter(windowEnd)) continue;

            // occurredAt은 nowHour로 통일, payload는 그대로 유지
            kept.add(new AlertEvent(e.type(), e.regionId(), nowHour, e.payload()));
        }

        if (kept.isEmpty()) return List.of();

        // validAt 기준 정렬 후 최대 cap개 유지(중복/오염 방어)
        kept.sort(Comparator.comparing(this::readValidAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (kept.size() > cap) {
            return List.copyOf(kept.subList(0, cap));
        }
        return List.copyOf(kept);
    }

    private int computeEndOffset(@Nullable Integer hourLimitInclusive) {
        if (maxWindowHours == 0) return 0;
        if (hourLimitInclusive == null) return maxWindowHours;

        int limit = Math.max(0, hourLimitInclusive);
        return Math.min(maxWindowHours, limit);
    }

    @Nullable
    private LocalDateTime readValidAt(@Nullable AlertEvent e) {
        if (e == null || e.payload() == null) return null;
        if (e.payload() instanceof ValidAtPayload v) return v.validAt();
        return null;
    }
}
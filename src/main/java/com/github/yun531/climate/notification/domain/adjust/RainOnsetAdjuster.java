package com.github.yun531.climate.notification.domain.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RainOnset AlertEvent를 now 기준 effectiveTime 윈도우로 필터링.
 * - window: [nowHour + startOffset, nowHour + endOffset]
 * - 윈도우 밖 이벤트 제거, occurredAt은 nowHour로 통일
 */
public class RainOnsetAdjuster {

    private static final Comparator<AlertEvent> BY_VALID_AT = Comparator.comparing(
            ev -> ((RainOnsetPayload) ev.payload()).validAt(),
            Comparator.nullsLast(Comparator.naturalOrder()));

    private final int horizonHours;     // 기본 24
    private final int startOffsetHours; // 기본 1 (now+1부터)

    public RainOnsetAdjuster(int horizonHours) {
        this(horizonHours, 1);
    }

    public RainOnsetAdjuster(int horizonHours, int startOffsetHours) {
        this.horizonHours = Math.max(0, horizonHours);
        this.startOffsetHours = Math.max(0, startOffsetHours);
    }

    /** withinHours 으로 윈도우를 축소할 수 있다. null 이면 horizonHours 사용 */
    public List<AlertEvent> adjust(List<AlertEvent> events, LocalDateTime now,
                                   @Nullable Integer withinHours) {
        if (events == null || events.isEmpty()) return List.of();
        if (now == null) return List.copyOf(events);

        int endOffset = computeEndOffset(withinHours);
        if (endOffset < startOffsetHours) return List.of();

        LocalDateTime nowHour = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime windowStart = nowHour.plusHours(startOffsetHours);
        LocalDateTime windowEnd   = nowHour.plusHours(endOffset);

        List<AlertEvent> kept = filterByWindow(events, nowHour, windowStart, windowEnd);
        if (kept.isEmpty()) return List.of();

        kept.sort(BY_VALID_AT);
        int maxCount = endOffset - startOffsetHours + 1;
        return (kept.size() > maxCount) ? List.copyOf(kept.subList(0, maxCount)) : List.copyOf(kept);
    }

    /** 윈도우 범위 내 이벤트만 남기고, occurredAt을 nowHour로 통일 */
    private List<AlertEvent> filterByWindow(
            List<AlertEvent> events, LocalDateTime nowHour,
            LocalDateTime windowStart, LocalDateTime windowEnd
    ) {
        List<AlertEvent> out = new ArrayList<>(events.size());

        for (AlertEvent e : events) {
            if (!(e.payload() instanceof RainOnsetPayload p)) {
                throw new IllegalArgumentException(
                        "RainOnsetAdjuster expects RainOnsetPayload, got: "
                                + e.payload().getClass().getSimpleName());
            }

            LocalDateTime validAt = p.validAt();
            if (validAt == null) continue;
            if (validAt.isBefore(windowStart) || validAt.isAfter(windowEnd)) continue;

            out.add(new AlertEvent(e.type(), e.regionId(), nowHour, e.payload()));
        }
        return out;
    }

    /** hourLimit이 있으면 horizonHours와 비교해 더 작은 쪽 사용 */
    private int computeEndOffset(@Nullable Integer hourLimit) {
        if (hourLimit == null) return horizonHours;
        return Math.min(horizonHours, Math.max(0, hourLimit));
    }
}
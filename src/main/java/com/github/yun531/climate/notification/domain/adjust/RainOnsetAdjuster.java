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
 * RainOnset AlertEvent를 now 기준 validAt 윈도우로 필터링.
 * - window: [nowHour + startOffsetHours(1), nowHour + endOffset]
 * - 윈도우 밖 이벤트 제거, occurredAt은 nowHour로 통일, now는 truncatedTo(HOURS)로 정각 기준
 */
public class RainOnsetAdjuster {

    private final int horizonHours;     // 기본 24
    private final int startOffsetHours; // 기본 1 (now+1부터)

    public RainOnsetAdjuster(int horizonHours) {
        this(horizonHours, 1);
    }

    public RainOnsetAdjuster(int horizonHours, int startOffsetHours) {
        this.horizonHours = Math.max(0, horizonHours);
        this.startOffsetHours = Math.max(0, startOffsetHours);
    }

    /** @param hourLimit now 기준 N시간 이내 제한 (null 이면 horizonHours 사용) */
    public List<AlertEvent> adjust(List<AlertEvent> events, LocalDateTime now,
                                   @Nullable Integer hourLimit) {
        if (events == null || events.isEmpty()) return List.of();
        if (now == null) return List.copyOf(events);

        LocalDateTime nowHour = now.truncatedTo(ChronoUnit.HOURS);

        int endOffset = computeEndOffset(hourLimit);
        if (endOffset < startOffsetHours) return List.of();

        LocalDateTime windowStart = nowHour.plusHours(startOffsetHours);
        LocalDateTime windowEnd   = nowHour.plusHours(endOffset);
        int maxEventCount = endOffset - startOffsetHours + 1;

        ArrayList<AlertEvent> kept = new ArrayList<>(Math.min(events.size(), maxEventCount));

        for (AlertEvent e : events) {
            if (!(e.payload() instanceof RainOnsetPayload p)) continue;

            LocalDateTime validAt = p.validAt();
            if (validAt == null) continue;
            if (validAt.isBefore(windowStart) || validAt.isAfter(windowEnd)) continue;

            kept.add(new AlertEvent(e.type(), e.regionId(), nowHour, e.payload()));
        }

        if (kept.isEmpty()) return List.of();

        // validAt 기준 정렬 후 최대 maxEventCount개
        kept.sort(Comparator.comparing(
                ev -> ((RainOnsetPayload) ev.payload()).validAt(),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        if (kept.size() > maxEventCount) return List.copyOf(kept.subList(0, maxEventCount));
        return List.copyOf(kept);
    }

    private int computeEndOffset(@Nullable Integer hourLimit) {
        if (hourLimit == null) return horizonHours;
        return Math.min(horizonHours, Math.max(0, hourLimit));
    }
}
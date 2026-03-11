package com.github.yun531.climate.snapshot.domain.policy;

import com.github.yun531.climate.snapshot.domain.model.SnapKind;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기상 발표 스케줄 정책.
 * 발표 시각은 3시간 간격(02,05,08,11,14,17,20,23)이며,
 * 발표 후 availableDelayMinutes(기본 10분) 경과 시점부터 접근 가능으로 판정.
 */
@Component
public class PublishSchedulePolicy {

    private static final List<Integer> ANNOUNCE_HOURS = List.of(2, 5, 8, 11, 14, 17, 20, 23);

    private final int availableDelayMinutes;

    public PublishSchedulePolicy() {
        this(10);
    }

    public PublishSchedulePolicy(int availableDelayMinutes) {
        this.availableDelayMinutes = availableDelayMinutes;
    }

    /** now >= announceTime + delay 이면 접근 가능 */
    public boolean isAccessible(LocalDateTime now, LocalDateTime announceTime) {
        return now != null
                && announceTime != null
                && !now.isBefore(announceTime.plusMinutes(availableDelayMinutes));
    }

    /** SnapKind에 따른 기준 발표시각. PREVIOUS는 CURRENT 보다 3시간 이전 */
    public LocalDateTime announceTimeFor(LocalDateTime now, SnapKind kind) {
        LocalDateTime cur = latestAvailableAnnounceTime(now);
        if (cur == null || kind == null) return null;

        return switch (kind) {
            case CURRENT -> cur;
            case PREVIOUS -> cur.minusHours(3);
        };
    }

    /** now 기준 접근 가능한 최신 발표시각. cutoff(now - delay) 이하인 후보 중 최대 */
    public LocalDateTime latestAvailableAnnounceTime(LocalDateTime now) {
        if (now == null) return null;

        LocalDateTime cutoff = now.minusMinutes(availableDelayMinutes);
        LocalDate today = cutoff.toLocalDate();

        // cutoff 이하인 가장 늦은 발표시각을 역순 탐색
        for (int i = ANNOUNCE_HOURS.size() - 1; i >= 0; i--) {
            LocalDateTime t = today.atTime(ANNOUNCE_HOURS.get(i), 0);

            if (!t.isAfter(cutoff)) return t;
        }

        // 오늘 후보가 모두 cutoff 이후 → 전날 마지막 발표시각
        LocalDate yesterday = today.minusDays(1);

        return yesterday.atTime(ANNOUNCE_HOURS.get(ANNOUNCE_HOURS.size() - 1), 0);
    }
}
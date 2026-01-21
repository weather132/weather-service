package com.github.yun531.climate.service.snapshot.policy;

import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AnnounceTimePolicy {

    // 발표 시각: 02,05,08,11,14,17,20,23
    private static final List<Integer> ANNOUNCE_HOURS = List.of(2, 5, 8, 11, 14, 17, 20, 23);
    private final int availableDelayMinutes;

    public AnnounceTimePolicy() {
        this(10);   // “발표 후 10분부터 접근 가능”
    }

    public AnnounceTimePolicy(int availableDelayMinutes) {
        this.availableDelayMinutes = availableDelayMinutes;
    }

    public boolean isAccessible(LocalDateTime now, LocalDateTime announceTime) {
        return now != null
                && announceTime != null
                && !now.isBefore(announceTime.plusMinutes(availableDelayMinutes));
    }

    public LocalDateTime resolve(LocalDateTime now, int snapId) {
        int cur = SnapKindEnum.SNAP_CURRENT.getCode();
        int prv = SnapKindEnum.SNAP_PREVIOUS.getCode();

        if (snapId == cur) {
            return latestAvailableAnnounceTime(now);
        }
        if (snapId == prv) {
            LocalDateTime curTime = latestAvailableAnnounceTime(now);
            return curTime == null ? null : curTime.minusHours(3);
        }
        return null;
    }

    /**
     * now 기준 “접근 가능한” 최신 발표시각:
     * cutoff = now - delay, cutoff 이하인 발표시각 중 최대
     */
    public LocalDateTime latestAvailableAnnounceTime(LocalDateTime now) {
        if (now == null) return null;

        LocalDateTime cutoff = now.minusMinutes(availableDelayMinutes);

        LocalDate d0 = cutoff.toLocalDate();
        LocalDate d1 = d0.minusDays(1);

        LocalDateTime best = null;

        for (int h : ANNOUNCE_HOURS) {
            LocalDateTime t0 = d0.atTime(h, 0);
            if (!t0.isAfter(cutoff) && (best == null || t0.isAfter(best))) best = t0;

            LocalDateTime t1 = d1.atTime(h, 0);
            if (!t1.isAfter(cutoff) && (best == null || t1.isAfter(best))) best = t1;
        }

        return best;
    }
}
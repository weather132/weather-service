package com.github.yun531.climate.snapshot.domain.policy;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
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
        this(10); // “발표 후 10분부터 접근 가능”
    }

    public AnnounceTimePolicy(int availableDelayMinutes) {
        this.availableDelayMinutes = availableDelayMinutes;
    }

    /** now 시각에 announceTime 데이터 접근 가능 여부 */
    public boolean isAccessible(LocalDateTime now, LocalDateTime announceTime) {
        return now != null
                && announceTime != null
                && !now.isBefore(announceTime.plusMinutes(availableDelayMinutes));
    }

    /** CURRENT / PREVIOUS 에 따른 기준 발표시각 반환 */
    public LocalDateTime resolve(LocalDateTime now, SnapKind kind) {
        LocalDateTime cur = latestAvailableAnnounceTime(now);
        if (cur == null || kind == null) return null;

        return switch (kind) {
            case CURRENT -> cur;
            case PREVIOUS -> cur.minusHours(3);
        };
    }

    /** now 기준 “접근 가능한” 최신 발표시각:
     *  cutoff = now - delay, cutoff 이하인 발표시각 중 최대 */
    public LocalDateTime latestAvailableAnnounceTime(LocalDateTime now) {
        if (now == null) return null;

        LocalDateTime cutoff = now.minusMinutes(availableDelayMinutes);
        LocalDate d0 = cutoff.toLocalDate();
        LocalDate d1 = d0.minusDays(1);

        LocalDateTime best = null;

        // 오늘 후보
        best = maxCandidateUpTo(cutoff, d0, best);
        // 전날 후보
        best = maxCandidateUpTo(cutoff, d1, best);

        return best;
    }

    /** day의 발표시각 후보들 중 cutoff 이하인 최대를, currentBest와 비교해 갱신 */
    private LocalDateTime maxCandidateUpTo(LocalDateTime cutoff, LocalDate day, LocalDateTime currentBest) {
        LocalDateTime best = currentBest;

        for (int h : ANNOUNCE_HOURS) {
            LocalDateTime t = day.atTime(h, 0);
            if (!t.isAfter(cutoff) && (best == null || t.isAfter(best))) {
                best = t;
            }
        }
        return best;
    }
}
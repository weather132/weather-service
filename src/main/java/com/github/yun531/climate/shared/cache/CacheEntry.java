package com.github.yun531.climate.shared.cache;

import java.time.LocalDateTime;

/** 캐시 값 + 기준 시각(anchor)
 *   anchor는 용도에 따라 의미가 달라진다:
 *     TTL 캐시            -> 실제 계산 시각(now)
 *     Reference-time 캐시 -> 데이터 발표시각(reportTime) 등 외부 기준 시각
 */
public record CacheEntry<T>(
        T value,
        LocalDateTime anchor
) {
    /**
     * referenceTime이 anchor + toleranceMinutes를 넘으면 stale.
     * referenceTime 또는 anchor가 null 이면 항상 stale.
     */
    public boolean isStale(LocalDateTime referenceTime, int toleranceMinutes) {
        if (referenceTime == null || anchor == null) return true;

        LocalDateTime expiresAt = anchor.plusMinutes(Math.max(0, toleranceMinutes));
        return referenceTime.isAfter(expiresAt);
    }
}
package com.github.yun531.climate.shared.cache;

import java.time.LocalDateTime;

/** 캐시 값 + 계산 시각 */
public record CacheEntry<T>(
        T value,
        LocalDateTime computedAt
) {
    /**
     * referenceTime 기준으로 캐시가 재계산이 필요한지 판단한다.
     * - referenceTime == null 이면 항상 재계산
     * - computedAt == null 이면 재계산
     * - computedAt < (referenceTime - ttlMinutes) 이면 재계산
     */
    public boolean needsRecompute(LocalDateTime referenceTime, int ttlMinutes) {
        if (referenceTime == null) return true;
        if (computedAt == null) return true;

        int w = Math.max(0, ttlMinutes);
        LocalDateTime cutoff = referenceTime.minusMinutes(w);
        return computedAt.isBefore(cutoff);
    }
}
package com.github.yun531.climate.util.cache;

import java.time.LocalDateTime;

/** 캐시 값 + 계산 시각 */
public record CacheEntry<T>(
        T value,
        LocalDateTime computedAt
) {
    /**
     * since 기준으로 캐시가 재계산이 필요한지 판단한다.
     * - since == null 이면 항상 재계산
     * - entry/computedAt == null 이면 재계산
     * - computedAt < (since - thresholdMinutes) 이면 재계산
     */
    public boolean needsRecomputeSinceBased(LocalDateTime since, int thresholdMinutes) {
        if (since == null) return true;
        if (computedAt == null) return true;

        LocalDateTime floor = since.minusMinutes(thresholdMinutes);
        return computedAt.isBefore(floor);
    }
}
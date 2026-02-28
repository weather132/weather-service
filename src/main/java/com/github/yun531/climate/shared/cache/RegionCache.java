package com.github.yun531.climate.shared.cache;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** regionId(String 키) 기준의 In-memory 캐시 유틸 */
public class RegionCache<T> {

    private final Map<String, CacheEntry<T>> map = new ConcurrentHashMap<>();

    public CacheEntry<T> get(String regionId) {
        return map.get(regionId);
    }

    public void putEntry(String regionId, CacheEntry<T> entry) {
        map.put(regionId, entry);
    }

    /**
     *  - since == null 이면 무조건 재계산
     *  - now - computedAt >= ttlMinutes 이면 재계산
     *  - compute()를 사용해 원자적으로 갱신한다(동시성 중복 계산 감소)
     */
    public CacheEntry<T> getOrComputeSinceBased(
            String regionId,
            LocalDateTime since,
            int ttlMinutes,
            Supplier<CacheEntry<T>> computer
    ) {
        if (computer == null) throw new IllegalArgumentException("computer must not be null");

        return map.compute(regionId, (k, oldEntry) -> {
            if (oldEntry == null) return computer.get();
            if (oldEntry.needsRecomputeSinceBased(since, ttlMinutes)) return computer.get();
            return oldEntry;
        });
    }

    /**
     * TTL 기반 캐시
     * - now - computedAt >= ttlMinutes 이면 재계산
     * - since 기반 트릭(since=now)을 외부에서 숨겨서 의미를 명확히 한다.
     */
    public CacheEntry<T> getOrComputeTtlBased(
            String regionId,
            LocalDateTime now,
            int ttlMinutes,
            Supplier<CacheEntry<T>> computer
    ) {
        if (now == null) throw new IllegalArgumentException("now must not be null");
        return getOrComputeSinceBased(regionId, now, ttlMinutes, computer);
    }
}
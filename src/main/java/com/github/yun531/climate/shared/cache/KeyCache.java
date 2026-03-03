package com.github.yun531.climate.shared.cache;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** String key 기반 In-memory 캐시 유틸 */
public class KeyCache<T> {

    private final Map<String, CacheEntry<T>> cacheEntries = new ConcurrentHashMap<>();

    public CacheEntry<T> get(String key) {
        return cacheEntries.get(key);
    }

    /**
     * TTL 기반 캐시
     * - now - computedAt >= ttlMinutes 이면 재계산
     */
    public CacheEntry<T> getOrComputeByTtl(
            String key,
            LocalDateTime now,
            int ttlMinutes,
            Supplier<CacheEntry<T>> entryLoader
    ) {
        if (now == null) throw new IllegalArgumentException("now must not be null");
        return getOrComputeByReferenceTime(key, now, ttlMinutes, entryLoader);
    }

    /**
     *  - referenceTime == null 이면 무조건 재계산
     *  - referenceTime - computedAt >= ttlMinutes 이면 재계산
     *  - compute()를 사용해 원자적으로 갱신한다(동시성 중복 계산 감소)
     */
    public CacheEntry<T> getOrComputeByReferenceTime(
            String key,
            LocalDateTime referenceTime,
            int ttlMinutes,
            Supplier<CacheEntry<T>> entryLoader
    ) {
        if (entryLoader == null) throw new IllegalArgumentException("loader must not be null");

        return cacheEntries.compute(key, (k, oldEntry) -> {
            if (oldEntry == null) return entryLoader.get();
            if (oldEntry.needsRecompute(referenceTime, ttlMinutes)) return entryLoader.get();
            return oldEntry;
        });
    }
}
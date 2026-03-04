package com.github.yun531.climate.shared.cache;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** String key 기반 In-memory 캐시 유틸 */
public class KeyCache<T> {

    private final Map<String, CacheEntry<T>> entries = new ConcurrentHashMap<>();

    public CacheEntry<T> get(String key) {
        return entries.get(key);
    }

    /**
     * referenceTime 기준 캐시 조회.
     * - referenceTime이 null 이면 무조건 재계산
     * - getOrCompute = now 인 경우, anchor + toleranceMinutes < now 이면 재계산
     * - anchor + toleranceMinutes < referenceTime 이면 재계산
     */
    public CacheEntry<T> getOrCompute(
            String key,
            LocalDateTime referenceTime,
            int toleranceMinutes,
            Supplier<CacheEntry<T>> loader
    ) {
        if (loader == null) throw new IllegalArgumentException("loader must not be null");

        return entries.compute(key, (k, old) -> {
            if (old == null || old.isStale(referenceTime, toleranceMinutes)) {
                return loader.get();
            }
            return old;
        });
    }
}
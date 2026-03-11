package com.github.yun531.climate.shared.cache;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * String key 기반 in-memory 캐시.
 * stale 판정은 {@link CacheEntry#isStale}에 위임한다.
 */
public class KeyCache<T> {

    private final Map<String, CacheEntry<T>> entries = new ConcurrentHashMap<>();

    /**
     * 캐시 히트 시 기존 값 반환, stale 이면 loader로 재계산.
     * stale 판정은 {@link CacheEntry#isStale}에 위임한다.
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
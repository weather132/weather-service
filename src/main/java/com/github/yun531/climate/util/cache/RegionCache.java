package com.github.yun531.climate.util.cache;

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
     *  - 아니면 since - thresholdMinutes 보다 computedAt 가 이전이면 재계산
     *  - compute()를 사용해 원자적으로 갱신한다(동시성 중복 계산 감소)
     */
    public CacheEntry<T> getOrComputeSinceBased(
            String regionId,
            LocalDateTime since,
            int thresholdMinutes,
            Supplier<CacheEntry<T>> computer
    ) {
        if (computer == null) throw new IllegalArgumentException("computer must not be null");

        return map.compute(regionId, (k, oldEntry) -> {
            if (oldEntry == null) return computer.get();
            if (oldEntry.needsRecomputeSinceBased(since, thresholdMinutes)) return computer.get();
            return oldEntry;
        });
    }

    /** 캐시 무효화 */
    public void invalidate(String regionId) {
        map.remove(regionId);
    }

    public void invalidateAll() {
        map.clear();
    }
}
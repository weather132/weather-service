package com.github.yun531.climate.util.cache;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** regionId(정수 키) 기준의 In-memory 캐시 유틸 */
public class RegionCache<T> {

    private final Map<String, CacheEntry<T>> map = new ConcurrentHashMap<>();

    public CacheEntry<T> get(String regionId) {
        return map.get(regionId);
    }

    public void put(String regionId, T value, LocalDateTime computedAt) {
        map.put(regionId, new CacheEntry<>(value, computedAt));
    }

    public void putEntry(String regionId, CacheEntry<T> entry) {
        map.put(regionId, entry);
    }

    /**
     *  - since == null 이면 무조건 재계산
     *  - 아니면 since - thresholdMinutes 보다 computedAt 가 이전이면 재계산
     *
     *  computer: 캐시 미존재/만료 시 새 CacheEntry<T>를 만드는 함수
     */
    public CacheEntry<T> getOrComputeSinceBased(
            String regionId,
            LocalDateTime since,
            int thresholdMinutes,
            Supplier<CacheEntry<T>> computer      // 람다(함수 객체)
    ) {
        CacheEntry<T> entry = get(regionId);

        if (needsRecomputeSinceBased(entry, since, thresholdMinutes)) {
            entry = computer.get();
            putEntry(regionId, entry);
        }
        return entry;
    }

    private boolean needsRecomputeSinceBased(
            CacheEntry<T> entry,
            LocalDateTime since,
            int thresholdMinutes
    ) {
        if (since == null) return true;
        if (entry == null || entry.computedAt() == null) return true;

        LocalDateTime floor = since.minusMinutes(thresholdMinutes);
        return entry.computedAt().isBefore(floor);
    }

    /** 캐시 무효화 */
    public void invalidate(String regionId) {
        map.remove(regionId);
    }
    public void invalidateAll() {
        map.clear();
    }
}

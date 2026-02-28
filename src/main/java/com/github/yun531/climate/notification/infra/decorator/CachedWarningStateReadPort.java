package com.github.yun531.climate.notification.infra.decorator;

import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.RegionCache;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.Map;

/**
* 룰 결과가 아니라, 포트 조회 결과(Map)를 TTL로 캐시한다.
 */
public class CachedWarningStateReadPort implements WarningStateReadPort {

    private final WarningStateReadPort delegate;
    private final int ttlMinutes;

    private final RegionCache<Map<WarningKind, WarningStateView>> cache = new RegionCache<>();

    public CachedWarningStateReadPort(WarningStateReadPort delegate, int ttlMinutes) {
        this.delegate = delegate;
        this.ttlMinutes = Math.max(0, ttlMinutes);
    }

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        if (regionId == null || regionId.isBlank()) return Map.of();

        LocalDateTime now = TimeUtil.nowMinutes();

        CacheEntry<Map<WarningKind, WarningStateView>> entry = cache.getOrComputeTtlBased(
                regionId,
                now,
                ttlMinutes,
                () -> {
                    Map<WarningKind, WarningStateView> loaded = delegate.loadLatestByKind(regionId);
                    Map<WarningKind, WarningStateView> safe = (loaded == null) ? Map.of() : loaded;
                    return new CacheEntry<>(safe, now);
                }
        );

        return (entry == null || entry.value() == null) ? Map.of() : entry.value();
    }
}
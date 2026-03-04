package com.github.yun531.climate.warning.infra.adapter;

import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.KeyCache;
import com.github.yun531.climate.shared.time.TimeUtil;
import com.github.yun531.climate.warning.infra.mapper.WarningStateViewMapper;
import com.github.yun531.climate.warning.infra.persistence.repository.WarningStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Primary
@RequiredArgsConstructor
public class JpaWarningStateReadAdapter implements WarningStateReadPort {

    private final WarningStateRepository repo;

    @Value("${notification.warning.cache-ttl-minutes:45}")
    private int ttlMinutes;

    private final KeyCache<Map<WarningKind, WarningStateView>> cache = new KeyCache<>();

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        if (regionId == null || regionId.isBlank()) return Map.of();

        LocalDateTime now = TimeUtil.nowTruncatedToMinute();

        CacheEntry<Map<WarningKind, WarningStateView>> entry = cache.getOrCompute(
                regionId,
                now,
                ttlMinutes,
                () -> {
                    var rows = repo.findByRegionIdIn(List.of(regionId));
                    var map = WarningStateViewMapper.pickLatestByKind(regionId, rows);
                    return new CacheEntry<>(map, now);
                }
        );

        return (entry == null || entry.value() == null) ? Map.of() : entry.value();
    }
}
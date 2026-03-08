package com.github.yun531.climate.warning.infra.reader;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.reader.WarningStateReader;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.KeyCache;
import com.github.yun531.climate.warning.infra.persistence.mapper.IssuedWarningMapper;
import com.github.yun531.climate.warning.infra.persistence.repository.WarningStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class JpaIssuedWarningReader implements WarningStateReader {

    private final WarningStateRepository repo;
    private final Clock clock;
    private final int ttlMinutes;

    private final KeyCache<Map<WarningKind, IssuedWarning>> cache = new KeyCache<>();

    public JpaIssuedWarningReader(
            WarningStateRepository repo,
            Clock clock,
            @Value("${notification.warning.cache-ttl-minutes:45}") int ttlMinutes
    ) {
        this.repo = repo;
        this.clock = clock;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public Map<WarningKind, IssuedWarning> loadLatestByKind(String regionId) {
        if (regionId == null || regionId.isBlank()) return Map.of();

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);

        CacheEntry<Map<WarningKind, IssuedWarning>> entry = cache.getOrCompute(
                regionId,
                now,
                ttlMinutes,
                () -> {
                    var rows = repo.findByRegionIdIn(List.of(regionId));
                    var map = IssuedWarningMapper.pickLatestByKind(regionId, rows);
                    return new CacheEntry<>(map, now);
                }
        );

        return (entry == null || entry.value() == null) ? Map.of() : entry.value();
    }
}
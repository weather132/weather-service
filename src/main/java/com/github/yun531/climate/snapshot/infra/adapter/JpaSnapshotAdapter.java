package com.github.yun531.climate.snapshot.infra.adapter;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.port.SnapshotPort;
import com.github.yun531.climate.kernel.snapshot.readmodel.WeatherSnapshot;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.KeyCache;
import com.github.yun531.climate.shared.time.TimeUtil;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.infra.mapper.SnapshotEntityMapper;
import com.github.yun531.climate.snapshot.infra.persistence.entity.SnapshotEntity;
import com.github.yun531.climate.snapshot.infra.persistence.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Primary    //todo  로컬 DB 사용해서 JPA 사용중
@RequiredArgsConstructor
public class JpaSnapshotAdapter implements SnapshotPort {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotCacheProperties cacheProps;
    private final PublishSchedulePolicy publishSchedule;
    private final SnapshotEntityMapper mapper;

    private final KeyCache<WeatherSnapshot> snapshotCache = new KeyCache<>();

    @Override
    @Nullable
    public WeatherSnapshot load(String regionId, SnapKind kind) {
        if (regionId == null || regionId.isBlank() || kind == null) return null;

        LocalDateTime now = TimeUtil.nowTruncatedToMinute();
        LocalDateTime publishTime = publishSchedule.resolve(now, kind);
        if (publishTime == null) return null;

        SnapshotKey snapshotKey = SnapshotKey.of(regionId, kind);

        return snapshotCache.getOrCompute(
                snapshotKey.asCacheKey(),
                publishTime,
                cacheProps.recomputeThresholdMinutes(),
                () -> fetchSnapshot(snapshotKey, now)
        ).value();
    }

    // =====================================================================
    //  snapshot 조회
    // =====================================================================

    /**
     * DB 에서 Entity를 조회해 WeatherSnapshot 으로 변환한다.
     * anchor = reportTime: 새 발표시각으로 점프하면 즉시 stale 판정.
     */
    private CacheEntry<WeatherSnapshot> fetchSnapshot(SnapshotKey snapshotKey, LocalDateTime now) {
        SnapshotEntity entity = snapshotRepository.findBySnapIdAndRegionId(
                snapshotKey.asSnapId(),
                snapshotKey.regionId()
        );

        if (entity == null) return emptyCacheEntry(now);

        WeatherSnapshot snapshot = mapper.toSnapshot(entity);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }

    private CacheEntry<WeatherSnapshot> emptyCacheEntry(LocalDateTime now) {
        return new CacheEntry<>(null, now);
    }
}
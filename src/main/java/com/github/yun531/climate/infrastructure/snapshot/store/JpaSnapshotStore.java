package com.github.yun531.climate.infrastructure.snapshot.store;

import com.github.yun531.climate.infrastructure.persistence.entity.ClimateSnap;
import com.github.yun531.climate.infrastructure.snapshot.config.SnapshotCacheProperties;
import com.github.yun531.climate.infrastructure.snapshot.assembler.JpaForecastSnapAssembler;
import com.github.yun531.climate.infrastructure.persistence.repository.ClimateSnapRepository;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.infrastructure.snapshot.policy.AnnounceTimePolicy;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.RegionCache;
import com.github.yun531.climate.shared.snapshot.SnapKind;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Primary    //todo  로컬 DB 사용해서 JPA 사용중
@RequiredArgsConstructor
public class JpaSnapshotStore implements SnapshotStore {

    private final ClimateSnapRepository climateSnapRepository;
    private final SnapshotCacheProperties cacheProps;
    private final AnnounceTimePolicy policy;
    private final JpaForecastSnapAssembler mapper;

    /** regionId + kind 기준 스냅샷 캐시 */
    private final RegionCache<ForecastSnap> snapCache = new RegionCache<>();

    @Override
    @Nullable
    public ForecastSnap load(String regionId, SnapKind kind) {
        if (regionId == null || regionId.isBlank() || kind == null) return null;

        LocalDateTime now = TimeUtil.nowMinutes();

        // since(=접근 가능한 최신 발표시각)  <-- 여기만 kind로 호출
        LocalDateTime since = policy.resolve(now, kind);
        if (since == null) since = now;

        int threshold = cacheProps.recomputeThresholdMinutes();
        String key = SnapKey.of(regionId, kind).asCacheKey();

        int snapId = SnapKindCodec.toCode(kind);
        return snapCache.getOrComputeSinceBased(
                key,
                since,
                threshold,
                () -> computeEntry(regionId, snapId, now)
        ).value();
    }

    @Override
    @Nullable
    public ForecastSnap loadBySnapId(String regionId, int snapId) {
        // CURRENT/PREVIOUS는 의미 단위로 정규화
        SnapKind kind = SnapKindCodec.fromCode(snapId);
        if (kind != null) return load(regionId, kind);

        // 그 외 snapId는 캐시 없이 직접 조회(기존 동작 유지)
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        return (snap == null) ? null : mapper.toSnapshot(snap);
    }

    /**
     * computedAt은 reportTime(발표시각)로 둔다.
     * - since가 새 발표시각으로 점프하면 즉시 stale 판정이 나도록 맞춘다.
     */
    private CacheEntry<ForecastSnap> computeEntry(String regionId, int snapId, LocalDateTime now) {
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        if (snap == null) return new CacheEntry<>(null, now);

        ForecastSnap snapshot = mapper.toSnapshot(snap);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }
}
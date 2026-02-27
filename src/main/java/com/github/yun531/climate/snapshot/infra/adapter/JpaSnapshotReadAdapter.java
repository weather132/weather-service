package com.github.yun531.climate.snapshot.infra.adapter;

import com.github.yun531.climate.snapshot.infra.persistence.entity.ClimateSnap;
import com.github.yun531.climate.snapshot.infra.persistence.repository.ClimateSnapRepository;
import com.github.yun531.climate.snapshot.infra.mapper.JpaSnapshotMapper;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.domain.policy.AnnounceTimePolicy;
import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.port.SnapshotReadPort;
import com.github.yun531.climate.kernel.snapshot.readmodel.Snapshot;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.RegionCache;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Primary    //todo  로컬 DB 사용해서 JPA 사용중
@RequiredArgsConstructor
public class JpaSnapshotReadAdapter implements SnapshotReadPort {

    private final ClimateSnapRepository climateSnapRepository;
    private final SnapshotCacheProperties cacheProps;
    private final AnnounceTimePolicy policy;
    private final JpaSnapshotMapper mapper;

    /** regionId + kind 기준 스냅샷 캐시 */
    private final RegionCache<Snapshot> snapCache = new RegionCache<>();

    @Override
    @Nullable
    public Snapshot load(String regionId, SnapKind kind) {
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

    /**
     * computedAt은 reportTime(발표시각)로 둔다.
     * - since가 새 발표시각으로 점프하면 즉시 stale 판정이 나도록 맞춘다.
     */
    private CacheEntry<Snapshot> computeEntry(String regionId, int snapId, LocalDateTime now) {
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        if (snap == null) return new CacheEntry<>(null, now);

        Snapshot snapshot = mapper.toSnapshot(snap);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }
}
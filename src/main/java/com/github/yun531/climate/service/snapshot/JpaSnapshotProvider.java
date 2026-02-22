package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.entity.ClimateSnap;
import com.github.yun531.climate.repository.ClimateSnapRepository;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.snapshot.mapper.JpaForecastSnapAssembler;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.service.snapshot.policy.AnnounceTimePolicy;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.RegionCache;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Primary // TODO: ApiSnapshotProvider로 전환 시 옮기기
@RequiredArgsConstructor
public class JpaSnapshotProvider implements SnapshotProvider {

    private static final int CUR = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int PRV = SnapKindEnum.SNAP_PREVIOUS.getCode();

    private final ClimateSnapRepository climateSnapRepository;
    private final SnapshotCacheProperties cacheProps;
    private final AnnounceTimePolicy policy;
    private final JpaForecastSnapAssembler mapper;

    /** regionId + snapId 기준 스냅샷 캐시(1개로 통합) */
    private final RegionCache<ForecastSnap> snapCache = new RegionCache<>();

    @Override
    @Nullable
    public ForecastSnap loadSnapshot(String regionId, int snapId) {
        LocalDateTime now = TimeUtil.nowMinutes();

        if (snapId == CUR || snapId == PRV) {
            LocalDateTime since = policy.resolve(now, snapId);
            if (since == null) since = now;

            int ttl = cacheProps.snapTtlMinutes();
            String key = snapKey(regionId, snapId);

            return snapCache.getOrComputeSinceBased(
                    key,
                    since,
                    ttl,
                    () -> computeSnapshotEntry(regionId, snapId, now)
            ).value();
        }

        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        return (snap == null) ? null : mapper.toSnapshot(snap);
    }

    /**
     * computedAt은 reportTime(발표시각)로 둔다.
     * - since가 새 발표시각으로 점프하면 즉시 stale 판정이 나도록 맞춘다.
     */
    private CacheEntry<ForecastSnap> computeSnapshotEntry(String regionId, int snapId, LocalDateTime now) {
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        if (snap == null) {
            return new CacheEntry<>(null, now);
        }

        ForecastSnap snapshot = mapper.toSnapshot(snap);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }

    private static String snapKey(String regionId, int snapId) {
        return regionId + ":" + snapId;
    }
}

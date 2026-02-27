package com.github.yun531.climate.snapshot.infra.adapter;

import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.api.SnapshotApiClient;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlySnapshotResponse;
import com.github.yun531.climate.snapshot.infra.mapper.ApiSnapshotMapper;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.domain.policy.AnnounceTimePolicy;
import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.port.SnapshotReadPort;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotDailyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.Snapshot;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.RegionCache;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiSnapshotReadAdapter implements SnapshotReadPort {

    private final SnapshotApiClient client;
    private final SnapshotCacheProperties cacheProps;
    private final AnnounceTimePolicy policy;
    private final ApiSnapshotMapper assembler;

    /** regionId + kind 기준 스냅샷 캐시 */
    private final RegionCache<Snapshot> snapCache = new RegionCache<>();
    /** daily는 announceTime 파라미터가 없으니 region 단위로 재사용 캐시 */
    private final RegionCache<List<SnapshotDailyPoint>> dailyPointsCache = new RegionCache<>();

    @Override
    @Nullable
    public Snapshot load(String regionId, SnapKind kind) {
        if (regionId == null || regionId.isBlank() || kind == null) return null;

        LocalDateTime now = TimeUtil.nowMinutes();

        LocalDateTime since = policy.resolve(now, kind);
        if (since == null) return null;

        int threshold = cacheProps.recomputeThresholdMinutes();
        String key = SnapKey.of(regionId, kind).asCacheKey();

        return snapCache.getOrComputeSinceBased(
                key,
                since,
                threshold,
                () -> computeEntry(regionId, now, since)
        ).value();
    }

    private CacheEntry<Snapshot> computeEntry(
            String regionId,
            LocalDateTime now,
            LocalDateTime announceTime
    ) {
        if (announceTime == null || !policy.isAccessible(now, announceTime)) {
            return new CacheEntry<>(null, now);
        }

        HourlySnapshotResponse hourly = client.fetchHourly(regionId, announceTime);
        if (hourly == null || hourly.gridForecastData() == null || hourly.gridForecastData().isEmpty()) {
            return new CacheEntry<>(null, now);
        }

        // daily는 region별 TTL 캐시 재사용 (since=now, threshold=dailyTtlMinutes)
        int dailyTtl = cacheProps.dailyTtlMinutes();
        List<SnapshotDailyPoint> dailyPoints = dailyPointsCache.getOrComputeSinceBased(
                regionId,
                now,
                dailyTtl,
                () -> {
                    var daily = client.fetchDaily(regionId);
                    var points = assembler.buildDailyPoints(hourly.announceTime().toLocalDate(), daily);
                    return new CacheEntry<>(points, now);
                }
        ).value();

        Snapshot snap = assembler.buildForecastSnap(regionId, hourly, dailyPoints);

        // computedAt은 reportTime(=발표시각)로 설정
        return new CacheEntry<>(snap, snap.reportTime());
    }
}
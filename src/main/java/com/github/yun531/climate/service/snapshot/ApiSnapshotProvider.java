package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.infra.snapshotapi.SnapshotApiClient;
import com.github.yun531.climate.infra.snapshotapi.dto.HourlySnapshotResponse;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.snapshot.mapper.ApiForecastSnapAssembler;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.service.snapshot.policy.AnnounceTimePolicy;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.cache.RegionCache;
import com.github.yun531.climate.util.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiSnapshotProvider implements SnapshotProvider {

    private static final int CUR = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int PRV = SnapKindEnum.SNAP_PREVIOUS.getCode();

    private final SnapshotApiClient client;
    private final SnapshotCacheProperties cacheProps;

    private final AnnounceTimePolicy policy;
    private final ApiForecastSnapAssembler assembler;

    /** regionId + snapId 기준 스냅샷 캐시 */
    private final RegionCache<ForecastSnap> snapCache = new RegionCache<>();
    /** daily는 announceTime 파라미터가 없으니 region 단위로 재사용 캐시 */
    private final RegionCache<List<DailyPoint>> dailyPointsCache = new RegionCache<>();

    @Override
    @Nullable
    public ForecastSnap loadSnapshot(String regionId, int snapId) {
        if (snapId != CUR && snapId != PRV) return null;

        LocalDateTime now = TimeUtil.nowMinutes();

        // since(= 기준 시각)를 "현재 시점에서 접근 가능한 최신 발표시각"으로 설정
        // - 이 값이 바뀌는 순간(새 발표 접근 가능 시점) 캐시가 즉시 갱신
        LocalDateTime since = policy.resolve(now, snapId);
        if (since == null) {
            // resolve가 null 이면 발표시각을 결정할 수 없는 상태이므로 반환
            return null;
        }

        int ttl = cacheProps.snapTtlMinutes();
        String key = snapKey(regionId, snapId);

        return snapCache.getOrComputeSinceBased(
                key,
                since,
                ttl,
                () -> compute(regionId, now, since)
        ).value();
    }

    private CacheEntry<ForecastSnap> compute(String regionId, LocalDateTime now, LocalDateTime announceTime) {
        if (announceTime == null || !policy.isAccessible(now, announceTime)) {
            return new CacheEntry<>(null, now);
        }

        HourlySnapshotResponse hourly = client.fetchHourly(regionId, announceTime);
        if (hourly == null || hourly.gridForecastData() == null || hourly.gridForecastData().isEmpty()) {
            return new CacheEntry<>(null, now);
        }

        // daily는 region별 캐시 재사용
        int dailyTtl = cacheProps.dailyTtlMinutes();
        List<DailyPoint> dailyPoints = dailyPointsCache.getOrComputeSinceBased(
                regionId, now, dailyTtl,
                () -> {
                    var daily = client.fetchDaily(regionId);
                    var points = assembler.buildDailyPoints(hourly.announceTime().toLocalDate(), daily);
                    return new CacheEntry<>(points, now);
                }
        ).value();

        ForecastSnap snap = assembler.buildForecastSnap(regionId, hourly, dailyPoints);

        // computedAt은 reportTime(=발표시각)로 설정
        return new CacheEntry<>(snap, snap.reportTime());
    }

    private static String snapKey(String regionId, int snapId) {
        return regionId + ":" + snapId;
    }
}
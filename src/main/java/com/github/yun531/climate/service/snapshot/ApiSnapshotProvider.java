package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.infra.snapshot.SnapshotApiClient;
import com.github.yun531.climate.infra.snapshot.dto.HourlySnapshotResponse;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.snapshot.mapper.ForecastSnapAssembler;
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

/**
 * API 기반 SnapshotProvider 구현체.
 * - CURRENT/PREV 발표시각을 정책(AnnounceTimePolicy)으로 계산
 * - hourly/daily API를 호출하여 ForecastSnap으로 조립
 * - regionId 기준 CURRENT/PREV 캐시 + daily 재사용 캐시
 */
@Component
@RequiredArgsConstructor
public class ApiSnapshotProvider implements SnapshotProvider {

    private final SnapshotApiClient client;
    private final SnapshotCacheProperties cacheProps;

    private final AnnounceTimePolicy policy;
    private final ForecastSnapAssembler assembler;

    /** regionId 별 CURRENT/PREV 스냅샷 캐시 */
    private final RegionCache<ForecastSnap> currentCache = new RegionCache<>();
    private final RegionCache<ForecastSnap> previousCache = new RegionCache<>();

    /** daily는 announceTime 파라미터가 없으니 region 단위로 재사용 캐시 */
    private final RegionCache<List<DailyPoint>> dailyPointsCache = new RegionCache<>();

    @Override
    @Nullable
    public ForecastSnap loadSnapshot(String regionId, int snapId) {
        LocalDateTime now = TimeUtil.nowMinutes();

        int cur = SnapKindEnum.SNAP_CURRENT.getCode();
        int prv = SnapKindEnum.SNAP_PREVIOUS.getCode();

        int snapTtl = cacheProps.snapTtlMinutes();
        if (snapId == cur) {
            return currentCache.getOrComputeSinceBased(
                    regionId, now, snapTtl,
                    () -> compute(regionId, now, cur)
            ).value();
        }

        if (snapId == prv) {
            return previousCache.getOrComputeSinceBased(
                    regionId, now, snapTtl,
                    () -> compute(regionId, now, prv)
            ).value();
        }

        return null;
    }

    private CacheEntry<ForecastSnap> compute(String regionId, LocalDateTime now, int snapId) {
        LocalDateTime announceTime = policy.resolve(now, snapId);

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
                    return new CacheEntry<>(points, now); // daily 캐시는 "지금 계산 시각" 기준 TTL
                }
        ).value();

        ForecastSnap snap = assembler.buildForecastSnap(regionId, hourly, dailyPoints);

        // 스냅샷 캐시는 발표시각(reportTime) 기준으로 computedAt 설정
        return new CacheEntry<>(snap, snap.reportTime());
    }
}
package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.entity.ClimateSnap;
import com.github.yun531.climate.repository.ClimateSnapRepository;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.service.snapshot.policy.AnnounceTimePolicy;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.cache.RegionCache;
import com.github.yun531.climate.util.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary // TODO: ApiSnapshotProvider로 전환 시 옮기기
@RequiredArgsConstructor
public class JpaSnapshotProvider implements SnapshotProvider {

    private static final int CUR = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int PRV = SnapKindEnum.SNAP_PREVIOUS.getCode();

    private final ClimateSnapRepository climateSnapRepository;
    private final SnapshotCacheProperties cacheProps;
    private final AnnounceTimePolicy policy;

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
        return (snap == null) ? null : toSnapshot(snap);
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

        ForecastSnap snapshot = toSnapshot(snap);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }

    private static String snapKey(String regionId, int snapId) {
        return regionId + ":" + snapId;
    }

    private ForecastSnap toSnapshot(ClimateSnap c) {
        return new ForecastSnap(
                c.getRegionId(),
                c.getReportTime(),
                buildHourlyPoints(c),
                buildDailyPoints(c)
        );
    }

    private List<HourlyPoint> buildHourlyPoints(ClimateSnap c) {
        List<HourlyPoint> list = new ArrayList<>(26);
        list.add(new HourlyPoint(c.getValidAtA01(), c.getTempA01(), c.getPopA01()));
        list.add(new HourlyPoint(c.getValidAtA02(), c.getTempA02(), c.getPopA02()));
        list.add(new HourlyPoint(c.getValidAtA03(), c.getTempA03(), c.getPopA03()));
        list.add(new HourlyPoint(c.getValidAtA04(), c.getTempA04(), c.getPopA04()));
        list.add(new HourlyPoint(c.getValidAtA05(), c.getTempA05(), c.getPopA05()));
        list.add(new HourlyPoint(c.getValidAtA06(), c.getTempA06(), c.getPopA06()));
        list.add(new HourlyPoint(c.getValidAtA07(), c.getTempA07(), c.getPopA07()));
        list.add(new HourlyPoint(c.getValidAtA08(), c.getTempA08(), c.getPopA08()));
        list.add(new HourlyPoint(c.getValidAtA09(), c.getTempA09(), c.getPopA09()));
        list.add(new HourlyPoint(c.getValidAtA10(), c.getTempA10(), c.getPopA10()));
        list.add(new HourlyPoint(c.getValidAtA11(), c.getTempA11(), c.getPopA11()));
        list.add(new HourlyPoint(c.getValidAtA12(), c.getTempA12(), c.getPopA12()));
        list.add(new HourlyPoint(c.getValidAtA13(), c.getTempA13(), c.getPopA13()));
        list.add(new HourlyPoint(c.getValidAtA14(), c.getTempA14(), c.getPopA14()));
        list.add(new HourlyPoint(c.getValidAtA15(), c.getTempA15(), c.getPopA15()));
        list.add(new HourlyPoint(c.getValidAtA16(), c.getTempA16(), c.getPopA16()));
        list.add(new HourlyPoint(c.getValidAtA17(), c.getTempA17(), c.getPopA17()));
        list.add(new HourlyPoint(c.getValidAtA18(), c.getTempA18(), c.getPopA18()));
        list.add(new HourlyPoint(c.getValidAtA19(), c.getTempA19(), c.getPopA19()));
        list.add(new HourlyPoint(c.getValidAtA20(), c.getTempA20(), c.getPopA20()));
        list.add(new HourlyPoint(c.getValidAtA21(), c.getTempA21(), c.getPopA21()));
        list.add(new HourlyPoint(c.getValidAtA22(), c.getTempA22(), c.getPopA22()));
        list.add(new HourlyPoint(c.getValidAtA23(), c.getTempA23(), c.getPopA23()));
        list.add(new HourlyPoint(c.getValidAtA24(), c.getTempA24(), c.getPopA24()));
        list.add(new HourlyPoint(c.getValidAtA25(), c.getTempA25(), c.getPopA25()));
        list.add(new HourlyPoint(c.getValidAtA26(), c.getTempA26(), c.getPopA26()));
        return List.copyOf(list);
    }

    private List<DailyPoint> buildDailyPoints(ClimateSnap c) {
        List<DailyPoint> list = new ArrayList<>(7);
        list.add(new DailyPoint(0, c.getTempA0dMin(), c.getTempA0dMax(), c.getPopA0dAm(), c.getPopA0dPm()));
        list.add(new DailyPoint(1, c.getTempA1dMin(), c.getTempA1dMax(), c.getPopA1dAm(), c.getPopA1dPm()));
        list.add(new DailyPoint(2, c.getTempA2dMin(), c.getTempA2dMax(), c.getPopA2dAm(), c.getPopA2dPm()));
        list.add(new DailyPoint(3, c.getTempA3dMin(), c.getTempA3dMax(), c.getPopA3dAm(), c.getPopA3dPm()));
        list.add(new DailyPoint(4, c.getTempA4dMin(), c.getTempA4dMax(), c.getPopA4dAm(), c.getPopA4dPm()));
        list.add(new DailyPoint(5, c.getTempA5dMin(), c.getTempA5dMax(), c.getPopA5dAm(), c.getPopA5dPm()));
        list.add(new DailyPoint(6, c.getTempA6dMin(), c.getTempA6dMax(), c.getPopA6dAm(), c.getPopA6dPm()));
        return List.copyOf(list);
    }
}
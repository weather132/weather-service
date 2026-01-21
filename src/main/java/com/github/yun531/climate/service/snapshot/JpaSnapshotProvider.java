package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.entity.ClimateSnap;
import com.github.yun531.climate.repository.ClimateSnapRepository;
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

/**
 * JPA 기반 SnapshotProvider 구현체.
 * climate_snap 테이블의 ClimateSnap 엔티티를 ForecastSnapshot 으로 변환하고,
 * regionId + snapId 기준으로 스냅샷을 캐시한다.
 */
@Component
@Primary    //todo: ApiSnapshotProvider 으로 수정할 때, 옮겨줘야 함
@RequiredArgsConstructor
public class JpaSnapshotProvider implements SnapshotProvider {

    private final ClimateSnapRepository climateSnapRepository;
    private final SnapshotCacheProperties cacheProps;

    /** regionId 별 CURRENT/PREVIOUS 스냅샷 캐시 */
    private final RegionCache<ForecastSnap> currentCache = new RegionCache<>();
    private final RegionCache<ForecastSnap> previousCache = new RegionCache<>();

    @Override
    @Nullable
    public ForecastSnap loadSnapshot(String regionId, int snapId) {
        LocalDateTime now = TimeUtil.nowMinutes();

        int curCode = SnapKindEnum.SNAP_CURRENT.getCode();
        int prevCode = SnapKindEnum.SNAP_PREVIOUS.getCode();

        if (snapId == curCode) {
            // CURRENT 스냅 캐시 사용
            CacheEntry<ForecastSnap> entry =
                    currentCache.getOrComputeSinceBased(
                            regionId,
                            now,
                            cacheProps.snapTtlMinutes(),
                            () -> computeSnapshotEntry(regionId, curCode)
                    );
            return entry.value();
        }

        if (snapId == prevCode) {
            // PREVIOUS 스냅 캐시 사용
            CacheEntry<ForecastSnap> entry =
                    previousCache.getOrComputeSinceBased(
                            regionId,
                            now,
                            cacheProps.snapTtlMinutes(),
                            () -> computeSnapshotEntry(regionId, prevCode)
                    );
            return entry.value();
        }

        // 기타 snapId에 대해서는 캐시 없이 바로 조회
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        if (snap == null) {
            return null;
        }
        return toSnapshot(snap);
    }

    /**
     * DB 에서 스냅을 조회해 ForecastSnapshot + CacheEntry로 감싸는 함수.
     * computedAt 에는 스냅의 reportTime을 그대로 사용한다.
     */
    private CacheEntry<ForecastSnap> computeSnapshotEntry(String regionId, int snapId) {
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        if (snap == null) {
            // 존재하지 않는 경우 캐시에 null을 넣을지, 아예 캐시하지 않을지는 정책에 따라 조정 가능
            return new CacheEntry<>(null, TimeUtil.nowMinutes());
        }

        ForecastSnap snapshot = toSnapshot(snap);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }

    /** ClimateSnap 엔티티 → ForecastSnapshot 도메인 모델 매핑 */
    private ForecastSnap toSnapshot(ClimateSnap c) {
        List<HourlyPoint> hourly = buildHourlyPoints(c);
        List<DailyPoint> daily = buildDailyPoints(c);

        return new ForecastSnap(
                c.getRegionId(),
                c.getReportTime(),
                hourly,
                daily
        );
    }

    /** 시간대별 (A01~A26) temp plus POP 매핑 */
    private List<HourlyPoint> buildHourlyPoints(ClimateSnap c) {
        List<HourlyPoint> list = new ArrayList<>(26);

        // A01~A26: (validAt, temp, pop)
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

    /** 일자별 (0~6일차) AM/PM temp + POP 매핑 */
    private List<DailyPoint> buildDailyPoints(ClimateSnap c) {
        List<DailyPoint> list = new ArrayList<>(7);

        list.add(new DailyPoint(
                0,
                c.getTempA0dMin(), c.getTempA0dMax(),
                c.getPopA0dAm(),  c.getPopA0dPm()
        ));
        list.add(new DailyPoint(
                1,
                c.getTempA1dMin(), c.getTempA1dMax(),
                c.getPopA1dAm(),  c.getPopA1dPm()
        ));
        list.add(new DailyPoint(
                2,
                c.getTempA2dMin(), c.getTempA2dMax(),
                c.getPopA2dAm(),  c.getPopA2dPm()
        ));
        list.add(new DailyPoint(
                3,
                c.getTempA3dMin(), c.getTempA3dMax(),
                c.getPopA3dAm(),  c.getPopA3dPm()
        ));
        list.add(new DailyPoint(
                4,
                c.getTempA4dMin(), c.getTempA4dMax(),
                c.getPopA4dAm(),  c.getPopA4dPm()
        ));
        list.add(new DailyPoint(
                5,
                c.getTempA5dMin(), c.getTempA5dMax(),
                c.getPopA5dAm(),  c.getPopA5dPm()
        ));
        list.add(new DailyPoint(
                6,
                c.getTempA6dMin(), c.getTempA6dMax(),
                c.getPopA6dAm(),  c.getPopA6dPm()
        ));

        return List.copyOf(list);
    }
}
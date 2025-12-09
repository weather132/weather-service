package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.ForecastSnapshot;
import com.github.yun531.climate.entity.ClimateSnap;
import com.github.yun531.climate.repository.ClimateSnapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA 기반 ForecastSnapshotProvider 구현체.
 * climate_snap 테이블의 ClimateSnap 엔티티를 ForecastSnapshot으로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class JpaForecastSnapshotProvider implements ForecastSnapshotProvider {

    private final ClimateSnapRepository climateSnapRepository;

    @Override
    @Nullable
    public ForecastSnapshot loadSnapshot(int regionId, int snapId) {
        ClimateSnap snap = climateSnapRepository.findBySnapIdAndRegionId(snapId, regionId);
        if (snap == null) {
            return null;
        }
        return toSnapshot(snap);
    }

    /** ClimateSnap 엔티티 → ForecastSnapshot 도메인 모델 매핑 */
    private ForecastSnapshot toSnapshot(ClimateSnap c) {
        List<ForecastSnapshot.HourlyPoint> hourly = buildHourlyPoints(c);
        List<ForecastSnapshot.DailyPoint> daily = buildDailyPoints(c);

        return new ForecastSnapshot(
                c.getRegionId(),
                c.getReportTime(),
                hourly,
                daily
        );
    }

    /** 시간대별 (0~23시간 후) temp + POP 매핑 */
    private List<ForecastSnapshot.HourlyPoint> buildHourlyPoints(ClimateSnap c) {
        List<ForecastSnapshot.HourlyPoint> list = new ArrayList<>(24);

        list.add(new ForecastSnapshot.HourlyPoint(0,  c.getTempA00(), c.getPopA00()));
        list.add(new ForecastSnapshot.HourlyPoint(1,  c.getTempA01(), c.getPopA01()));
        list.add(new ForecastSnapshot.HourlyPoint(2,  c.getTempA02(), c.getPopA02()));
        list.add(new ForecastSnapshot.HourlyPoint(3,  c.getTempA03(), c.getPopA03()));
        list.add(new ForecastSnapshot.HourlyPoint(4,  c.getTempA04(), c.getPopA04()));
        list.add(new ForecastSnapshot.HourlyPoint(5,  c.getTempA05(), c.getPopA05()));
        list.add(new ForecastSnapshot.HourlyPoint(6,  c.getTempA06(), c.getPopA06()));
        list.add(new ForecastSnapshot.HourlyPoint(7,  c.getTempA07(), c.getPopA07()));
        list.add(new ForecastSnapshot.HourlyPoint(8,  c.getTempA08(), c.getPopA08()));
        list.add(new ForecastSnapshot.HourlyPoint(9,  c.getTempA09(), c.getPopA09()));
        list.add(new ForecastSnapshot.HourlyPoint(10, c.getTempA10(), c.getPopA10()));
        list.add(new ForecastSnapshot.HourlyPoint(11, c.getTempA11(), c.getPopA11()));
        list.add(new ForecastSnapshot.HourlyPoint(12, c.getTempA12(), c.getPopA12()));
        list.add(new ForecastSnapshot.HourlyPoint(13, c.getTempA13(), c.getPopA13()));
        list.add(new ForecastSnapshot.HourlyPoint(14, c.getTempA14(), c.getPopA14()));
        list.add(new ForecastSnapshot.HourlyPoint(15, c.getTempA15(), c.getPopA15()));
        list.add(new ForecastSnapshot.HourlyPoint(16, c.getTempA16(), c.getPopA16()));
        list.add(new ForecastSnapshot.HourlyPoint(17, c.getTempA17(), c.getPopA17()));
        list.add(new ForecastSnapshot.HourlyPoint(18, c.getTempA18(), c.getPopA18()));
        list.add(new ForecastSnapshot.HourlyPoint(19, c.getTempA19(), c.getPopA19()));
        list.add(new ForecastSnapshot.HourlyPoint(20, c.getTempA20(), c.getPopA20()));
        list.add(new ForecastSnapshot.HourlyPoint(21, c.getTempA21(), c.getPopA21()));
        list.add(new ForecastSnapshot.HourlyPoint(22, c.getTempA22(), c.getPopA22()));
        list.add(new ForecastSnapshot.HourlyPoint(23, c.getTempA23(), c.getPopA23()));

        return List.copyOf(list);
    }

    /** 일자별 (0~6일차) AM/PM temp + POP 매핑 */
    private List<ForecastSnapshot.DailyPoint> buildDailyPoints(ClimateSnap c) {
        List<ForecastSnapshot.DailyPoint> list = new ArrayList<>(7);

        list.add(new ForecastSnapshot.DailyPoint(
                0,
                c.getTempA0dAm(), c.getTempA0dPm(),
                c.getPopA0dAm(),  c.getPopA0dPm()
        ));
        list.add(new ForecastSnapshot.DailyPoint(
                1,
                c.getTempA1dAm(), c.getTempA1dPm(),
                c.getPopA1dAm(),  c.getPopA1dPm()
        ));
        list.add(new ForecastSnapshot.DailyPoint(
                2,
                c.getTempA2dAm(), c.getTempA2dPm(),
                c.getPopA2dAm(),  c.getPopA2dPm()
        ));
        list.add(new ForecastSnapshot.DailyPoint(
                3,
                c.getTempA3dAm(), c.getTempA3dPm(),
                c.getPopA3dAm(),  c.getPopA3dPm()
        ));
        list.add(new ForecastSnapshot.DailyPoint(
                4,
                c.getTempA4dAm(), c.getTempA4dPm(),
                c.getPopA4dAm(),  c.getPopA4dPm()
        ));
        list.add(new ForecastSnapshot.DailyPoint(
                5,
                c.getTempA5dAm(), c.getTempA5dPm(),
                c.getPopA5dAm(),  c.getPopA5dPm()
        ));
        list.add(new ForecastSnapshot.DailyPoint(
                6,
                c.getTempA6dAm(), c.getTempA6dPm(),
                c.getPopA6dAm(),  c.getPopA6dPm()
        ));

        return List.copyOf(list);
    }
}

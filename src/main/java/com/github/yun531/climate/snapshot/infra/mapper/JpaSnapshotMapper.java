package com.github.yun531.climate.snapshot.infra.mapper;

import com.github.yun531.climate.snapshot.infra.persistence.entity.ClimateSnap;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotDailyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.Snapshot;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotHourlyPoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class JpaSnapshotMapper {

    private static final int HOURLY_SIZE = 26;
    private static final int DAILY_SIZE = 7;

    // ---- hourly getters (A01..A26) ----
    private static final List<Function<ClimateSnap, Integer>> TEMPS = List.of(
            ClimateSnap::getTempA01, ClimateSnap::getTempA02, ClimateSnap::getTempA03, ClimateSnap::getTempA04,
            ClimateSnap::getTempA05, ClimateSnap::getTempA06, ClimateSnap::getTempA07, ClimateSnap::getTempA08,
            ClimateSnap::getTempA09, ClimateSnap::getTempA10, ClimateSnap::getTempA11, ClimateSnap::getTempA12,
            ClimateSnap::getTempA13, ClimateSnap::getTempA14, ClimateSnap::getTempA15, ClimateSnap::getTempA16,
            ClimateSnap::getTempA17, ClimateSnap::getTempA18, ClimateSnap::getTempA19, ClimateSnap::getTempA20,
            ClimateSnap::getTempA21, ClimateSnap::getTempA22, ClimateSnap::getTempA23, ClimateSnap::getTempA24,
            ClimateSnap::getTempA25, ClimateSnap::getTempA26
    );

    private static final List<Function<ClimateSnap, Integer>> POPS = List.of(
            ClimateSnap::getPopA01, ClimateSnap::getPopA02, ClimateSnap::getPopA03, ClimateSnap::getPopA04,
            ClimateSnap::getPopA05, ClimateSnap::getPopA06, ClimateSnap::getPopA07, ClimateSnap::getPopA08,
            ClimateSnap::getPopA09, ClimateSnap::getPopA10, ClimateSnap::getPopA11, ClimateSnap::getPopA12,
            ClimateSnap::getPopA13, ClimateSnap::getPopA14, ClimateSnap::getPopA15, ClimateSnap::getPopA16,
            ClimateSnap::getPopA17, ClimateSnap::getPopA18, ClimateSnap::getPopA19, ClimateSnap::getPopA20,
            ClimateSnap::getPopA21, ClimateSnap::getPopA22, ClimateSnap::getPopA23, ClimateSnap::getPopA24,
            ClimateSnap::getPopA25, ClimateSnap::getPopA26
    );

    // ---- daily getters (0..6) ----
    private static final List<Function<ClimateSnap, Integer>> D_MIN = List.of(
            ClimateSnap::getTempA0dMin, ClimateSnap::getTempA1dMin, ClimateSnap::getTempA2dMin,
            ClimateSnap::getTempA3dMin, ClimateSnap::getTempA4dMin, ClimateSnap::getTempA5dMin,
            ClimateSnap::getTempA6dMin
    );

    private static final List<Function<ClimateSnap, Integer>> D_MAX = List.of(
            ClimateSnap::getTempA0dMax, ClimateSnap::getTempA1dMax, ClimateSnap::getTempA2dMax,
            ClimateSnap::getTempA3dMax, ClimateSnap::getTempA4dMax, ClimateSnap::getTempA5dMax,
            ClimateSnap::getTempA6dMax
    );

    private static final List<Function<ClimateSnap, Integer>> D_AM = List.of(
            ClimateSnap::getPopA0dAm, ClimateSnap::getPopA1dAm, ClimateSnap::getPopA2dAm,
            ClimateSnap::getPopA3dAm, ClimateSnap::getPopA4dAm, ClimateSnap::getPopA5dAm,
            ClimateSnap::getPopA6dAm
    );

    private static final List<Function<ClimateSnap, Integer>> D_PM = List.of(
            ClimateSnap::getPopA0dPm, ClimateSnap::getPopA1dPm, ClimateSnap::getPopA2dPm,
            ClimateSnap::getPopA3dPm, ClimateSnap::getPopA4dPm, ClimateSnap::getPopA5dPm,
            ClimateSnap::getPopA6dPm
    );

    public Snapshot toSnapshot(ClimateSnap c) {
        return new Snapshot(
                c.getRegionId(),
                c.getReportTime(),
                buildHourlyPoints(c),
                buildDailyPoints(c)
        );
    }

    private List<SnapshotHourlyPoint> buildHourlyPoints(ClimateSnap c) {
        // series_start_time 기반으로 재구성
        LocalDateTime base = (c.getSeriesStartTime() != null)
                ? c.getSeriesStartTime()
                : c.getReportTime().plusHours(1);

        List<SnapshotHourlyPoint> list = new ArrayList<>(HOURLY_SIZE);
        for (int i = 0; i < HOURLY_SIZE; i++) {
            LocalDateTime effectiveTime = base.plusHours(i);
            Integer temp = TEMPS.get(i).apply(c);
            Integer pop = POPS.get(i).apply(c);
            list.add(new SnapshotHourlyPoint(effectiveTime, temp, pop));
        }
        return List.copyOf(list);
    }

    private List<SnapshotDailyPoint> buildDailyPoints(ClimateSnap c) {
        List<SnapshotDailyPoint> list = new ArrayList<>(DAILY_SIZE);
        for (int d = 0; d < DAILY_SIZE; d++) {
            list.add(new SnapshotDailyPoint(
                    d,
                    D_MIN.get(d).apply(c),
                    D_MAX.get(d).apply(c),
                    D_AM.get(d).apply(c),
                    D_PM.get(d).apply(c)
            ));
        }
        return List.copyOf(list);
    }
}
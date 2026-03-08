package com.github.yun531.climate.snapshot.infra.persistence.mapper;

import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.infra.persistence.entity.SnapshotEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * JPA Entity(SnapshotEntity)를 내부 readmodel (WeatherSnapshot)로 변환한다.
 */
@Component
public class SnapshotEntityMapper {

    private static final int HOURLY_SIZE = 26;
    private static final int DAILY_SIZE = 7;

    public WeatherSnapshot toSnapshot(SnapshotEntity entity) {
        return new WeatherSnapshot(
                entity.getRegionId(),
                entity.getReportTime(),
                toHourlyPoints(entity),
                toDailyPoints(entity)
        );
    }

    private List<HourlyPoint> toHourlyPoints(SnapshotEntity entity) {
        LocalDateTime seriesStart = (entity.getSeriesStartTime() != null)
                ? entity.getSeriesStartTime()
                : entity.getReportTime().plusHours(1);

        List<HourlyPoint> points = new ArrayList<>(HOURLY_SIZE);
        for (int i = 0; i < HOURLY_SIZE; i++) {
            points.add(new HourlyPoint(
                    seriesStart.plusHours(i),
                    HOURLY_TEMP_GETTERS.get(i).apply(entity),
                    HOURLY_POP_GETTERS.get(i).apply(entity)
            ));
        }
        return List.copyOf(points);
    }

    private List<DailyPoint> toDailyPoints(SnapshotEntity entity) {
        List<DailyPoint> points = new ArrayList<>(DAILY_SIZE);
        for (int d = 0; d < DAILY_SIZE; d++) {
            points.add(new DailyPoint(
                    d,
                    DAILY_MIN_TEMP_GETTERS.get(d).apply(entity),
                    DAILY_MAX_TEMP_GETTERS.get(d).apply(entity),
                    DAILY_AM_POP_GETTERS.get(d).apply(entity),
                    DAILY_PM_POP_GETTERS.get(d).apply(entity)
            ));
        }
        return List.copyOf(points);
    }

    // =====================================================================
    //  Entity 플랫 컬럼 getter 매핑 (인덱스 → getter function)
    // =====================================================================

    private static final List<Function<SnapshotEntity, Integer>> HOURLY_TEMP_GETTERS = List.of(
            SnapshotEntity::getTempA01, SnapshotEntity::getTempA02, SnapshotEntity::getTempA03, SnapshotEntity::getTempA04,
            SnapshotEntity::getTempA05, SnapshotEntity::getTempA06, SnapshotEntity::getTempA07, SnapshotEntity::getTempA08,
            SnapshotEntity::getTempA09, SnapshotEntity::getTempA10, SnapshotEntity::getTempA11, SnapshotEntity::getTempA12,
            SnapshotEntity::getTempA13, SnapshotEntity::getTempA14, SnapshotEntity::getTempA15, SnapshotEntity::getTempA16,
            SnapshotEntity::getTempA17, SnapshotEntity::getTempA18, SnapshotEntity::getTempA19, SnapshotEntity::getTempA20,
            SnapshotEntity::getTempA21, SnapshotEntity::getTempA22, SnapshotEntity::getTempA23, SnapshotEntity::getTempA24,
            SnapshotEntity::getTempA25, SnapshotEntity::getTempA26
    );

    private static final List<Function<SnapshotEntity, Integer>> HOURLY_POP_GETTERS = List.of(
            SnapshotEntity::getPopA01, SnapshotEntity::getPopA02, SnapshotEntity::getPopA03, SnapshotEntity::getPopA04,
            SnapshotEntity::getPopA05, SnapshotEntity::getPopA06, SnapshotEntity::getPopA07, SnapshotEntity::getPopA08,
            SnapshotEntity::getPopA09, SnapshotEntity::getPopA10, SnapshotEntity::getPopA11, SnapshotEntity::getPopA12,
            SnapshotEntity::getPopA13, SnapshotEntity::getPopA14, SnapshotEntity::getPopA15, SnapshotEntity::getPopA16,
            SnapshotEntity::getPopA17, SnapshotEntity::getPopA18, SnapshotEntity::getPopA19, SnapshotEntity::getPopA20,
            SnapshotEntity::getPopA21, SnapshotEntity::getPopA22, SnapshotEntity::getPopA23, SnapshotEntity::getPopA24,
            SnapshotEntity::getPopA25, SnapshotEntity::getPopA26
    );

    private static final List<Function<SnapshotEntity, Integer>> DAILY_MIN_TEMP_GETTERS = List.of(
            SnapshotEntity::getTempA0dMin, SnapshotEntity::getTempA1dMin, SnapshotEntity::getTempA2dMin,
            SnapshotEntity::getTempA3dMin, SnapshotEntity::getTempA4dMin, SnapshotEntity::getTempA5dMin,
            SnapshotEntity::getTempA6dMin
    );

    private static final List<Function<SnapshotEntity, Integer>> DAILY_MAX_TEMP_GETTERS = List.of(
            SnapshotEntity::getTempA0dMax, SnapshotEntity::getTempA1dMax, SnapshotEntity::getTempA2dMax,
            SnapshotEntity::getTempA3dMax, SnapshotEntity::getTempA4dMax, SnapshotEntity::getTempA5dMax,
            SnapshotEntity::getTempA6dMax
    );

    private static final List<Function<SnapshotEntity, Integer>> DAILY_AM_POP_GETTERS = List.of(
            SnapshotEntity::getPopA0dAm, SnapshotEntity::getPopA1dAm, SnapshotEntity::getPopA2dAm,
            SnapshotEntity::getPopA3dAm, SnapshotEntity::getPopA4dAm, SnapshotEntity::getPopA5dAm,
            SnapshotEntity::getPopA6dAm
    );

    private static final List<Function<SnapshotEntity, Integer>> DAILY_PM_POP_GETTERS = List.of(
            SnapshotEntity::getPopA0dPm, SnapshotEntity::getPopA1dPm, SnapshotEntity::getPopA2dPm,
            SnapshotEntity::getPopA3dPm, SnapshotEntity::getPopA4dPm, SnapshotEntity::getPopA5dPm,
            SnapshotEntity::getPopA6dPm
    );
}
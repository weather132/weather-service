package com.github.yun531.climate.service.snapshot.mapper;

import com.github.yun531.climate.infra.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.infra.snapshotapi.dto.GridPoint;
import com.github.yun531.climate.infra.snapshotapi.dto.HourlySnapshotResponse;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ForecastSnapAssembler {
    /** 일별 예보(raw forecasts)를 서비스 모델(DailyPoint)로 집계하는 역할을 분리해 둔 컴포넌트
    /*  - 여기서는 단순히 위임만 하고, 실제 집계 로직은 DailyForecastAggregator에 둔다.       */
    private final DailyForecastAggregator dailyAgg = new DailyForecastAggregator();

    /*  시간별 스냅샷 + 일별 포인트를 합쳐 도메인 모델(ForecastSnap)을 생성 */
    public ForecastSnap buildForecastSnap(
            String regionId,
            HourlySnapshotResponse hourly,
            List<DailyPoint> dailyPoints
    ) {
        List<HourlyPoint> hourlyPoints = buildHourlyPoints(hourly);
        return new ForecastSnap(regionId, hourly.announceTime(), hourlyPoints, dailyPoints);
    }

    public List<HourlyPoint> buildHourlyPoints(HourlySnapshotResponse hourly) {
        List<GridPoint> src = hourly.gridForecastData() == null
                ? List.of()
                : hourly.gridForecastData();

        List<GridPoint> sorted = new ArrayList<>(src);
        sorted.sort(Comparator.comparing(GridPoint::effectiveTime, Comparator.nullsLast(Comparator.naturalOrder())));

        List<HourlyPoint> out = new ArrayList<>(Math.min(26, sorted.size()));
        for (int i = 0; i < sorted.size() && i < 26; i++) {
            GridPoint p = sorted.get(i);
            out.add(new HourlyPoint(p.effectiveTime(), p.temp(), p.pop()));
        }
        return List.copyOf(out);
    }

    public List<DailyPoint> buildDailyPoints(LocalDate baseDate, DailyForecastResponse daily) {
        if (daily == null || daily.forecasts() == null) {
            return emptyDailyPoints();
        }
        return dailyAgg.aggregate(baseDate, daily.forecasts());
    }

    private List<DailyPoint> emptyDailyPoints() {
        List<DailyPoint> out = new ArrayList<>(7);
        for (int d = 0; d < 7; d++) {
            out.add(new DailyPoint(d, null, null, null, null));
        }
        return List.copyOf(out);
    }
}
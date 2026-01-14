package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.util.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppForecastService {

    private final SnapshotQueryService snapshotQueryService;

    // 3시간 스냅샷을 0/1/2시간 재사용
    private final HourlyForecastWindowAdjuster windowAdjuster =
            new HourlyForecastWindowAdjuster(2);

    public HourlyForecastDto getHourlyForecast(String regionId) {
        HourlyForecastDto base = snapshotQueryService.getHourlyForecast(regionId);
        if (base == null) {
            return null;
        }
        return windowAdjuster.adjust(base, TimeUtil.nowMinutes());
    }

    public DailyForecastDto getDailyForecast(String regionId) {
        return snapshotQueryService.getDailyForecast(regionId);
    }
}
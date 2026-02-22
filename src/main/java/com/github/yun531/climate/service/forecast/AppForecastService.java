package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppForecastService {

    private final SnapshotQueryService snapshotQueryService;
    private final HourlyForecastWindowAdjuster windowAdjuster;

    public HourlyForecastDto getHourlyForecast(String regionId) {
        return getHourlyForecast(regionId, TimeUtil.nowMinutes());
    }

    public HourlyForecastDto getHourlyForecast(String regionId, LocalDateTime now) {
        LocalDateTime normalizedNow = (now == null) ? TimeUtil.nowMinutes() : TimeUtil.truncateToMinutes(now);

        HourlyForecastDto base = snapshotQueryService.getHourlyForecast(regionId);
        if (base == null) return null;   // 정책상 빈 DTO/Optional로 바꾸는 건 다음 단계에서 선택 가능

        return windowAdjuster.adjust(base, normalizedNow);
    }

    public DailyForecastDto getDailyForecast(String regionId) {
        return snapshotQueryService.getDailyForecast(regionId);
    }
}
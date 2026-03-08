package com.github.yun531.climate.forecast.application;

import com.github.yun531.climate.forecast.domain.adjust.HourlyForecastWindowAdjuster;
import com.github.yun531.climate.forecast.domain.reader.ForecastViewReader;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 일기예보 조회 서비스.
 * - ForecastViewReader(포트)에서 로드 → WindowAdjuster 적용
 */
public class ForecastService {

    private final ForecastViewReader viewReader;
    private final HourlyForecastWindowAdjuster windowAdjuster;
    private final Clock clock;

    public ForecastService(
            ForecastViewReader viewReader,
            HourlyForecastWindowAdjuster windowAdjuster,
            Clock clock
    ) {
        this.viewReader = viewReader;
        this.windowAdjuster = windowAdjuster;
        this.clock = clock;
    }

    // ======================= Hourly =======================

    public ForecastHourlyView getHourlyForecast(String regionId) {
        return getHourlyForecast(regionId, now());
    }

    public ForecastHourlyView getHourlyForecast(String regionId, LocalDateTime now) {
        LocalDateTime effectiveNow = normalizeNow(now);

        ForecastHourlyView base = viewReader.loadHourly(regionId);
        if (base == null) return null;

        return windowAdjuster.adjust(base, effectiveNow);
    }

    // ======================= Daily =======================

    public ForecastDailyView getDailyForecast(String regionId) {
        return viewReader.loadDaily(regionId);
    }

    // ======================= 시간 헬퍼 =======================

    private LocalDateTime normalizeNow(LocalDateTime now) {
        return (now == null) ? now() : now.truncatedTo(ChronoUnit.MINUTES);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }
}
package com.github.yun531.climate.forecast.application;

import com.github.yun531.climate.forecast.domain.adjust.ForecastWindowAdjuster;
import com.github.yun531.climate.forecast.domain.reader.ForecastViewReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ForecastApplicationConfig {

    @Bean
    public ForecastWindowAdjuster hourlyForecastWindowAdjuster(
            @Value("${forecast.hourly.max-shift-hours:2}") int maxShiftHours,
            @Value("${forecast.hourly.window-size:24}") int windowSize
    ) {
        return new ForecastWindowAdjuster(maxShiftHours, windowSize);
    }

    @Bean
    public ForecastService forecastService(
            ForecastViewReader viewReader,
            ForecastWindowAdjuster windowAdjuster,
            Clock clock
    ) {
        return new ForecastService(viewReader, windowAdjuster, clock);
    }
}
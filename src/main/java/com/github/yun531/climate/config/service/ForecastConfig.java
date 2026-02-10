package com.github.yun531.climate.config.service;

import com.github.yun531.climate.service.forecast.HourlyForecastWindowAdjuster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForecastConfig {

    @Bean
    public HourlyForecastWindowAdjuster hourlyForecastWindowAdjuster(
            @Value("${forecast.hourly.max-shift-hours:2}") int maxShiftHours,
            @Value("${forecast.hourly.window-size:24}") int windowSize
    ) {
        return new HourlyForecastWindowAdjuster(maxShiftHours, windowSize);
    }
}
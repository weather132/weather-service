package com.github.yun531.climate.notification.infra.alert;

import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.detect.RainForecastDetector;
import com.github.yun531.climate.notification.domain.detect.RainOnsetDetector;
import com.github.yun531.climate.notification.domain.detect.WarningIssuedDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlertInfraConfig {

    // ---- Detectors ----

    @Bean
    public RainOnsetDetector rainOnsetDetector(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxHourlyPoints
    ) {
        return new RainOnsetDetector(thresholdPop, maxHourlyPoints);
    }

    @Bean
    public RainForecastDetector rainForecastDetector(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxHourlyPoints
    ) {
        return new RainForecastDetector(thresholdPop, maxHourlyPoints);
    }

    @Bean
    public WarningIssuedDetector warningIssuedDetector() {
        return new WarningIssuedDetector();
    }

    // ---- Adjusters ----

    @Bean
    public RainOnsetAdjuster rainOnsetAdjuster(
            @Value("${notification.window-hours:24}") int windowHours
    ) {
        return new RainOnsetAdjuster(windowHours);
    }

    @Bean
    public RainForecastAdjuster rainForecastAdjuster(
            @Value("${notification.max-shift-hours:2}") int maxShiftHours,
            @Value("${notification.window-hours:24}") int windowHours
    ) {
        return new RainForecastAdjuster(maxShiftHours, windowHours, 1);
    }
}
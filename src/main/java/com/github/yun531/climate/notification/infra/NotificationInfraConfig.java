package com.github.yun531.climate.notification.infra;

import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.evaluator.RainForecastEvaluator;
import com.github.yun531.climate.notification.domain.evaluator.RainOnsetEvaluator;
import com.github.yun531.climate.notification.domain.evaluator.WarningIssuedEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationInfraConfig {

    // ---- Computers ----

    @Bean
    public RainOnsetEvaluator rainOnsetComputer(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxHourlyPoints
    ) {
        return new RainOnsetEvaluator(thresholdPop, maxHourlyPoints);
    }

    @Bean
    public RainForecastEvaluator rainForecastComputer(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxHourlyPoints
    ) {
        return new RainForecastEvaluator(thresholdPop, maxHourlyPoints);
    }

    @Bean
    public WarningIssuedEvaluator warningIssuedComputer() {
        return new WarningIssuedEvaluator();
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
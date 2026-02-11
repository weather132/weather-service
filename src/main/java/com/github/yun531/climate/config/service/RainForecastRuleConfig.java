package com.github.yun531.climate.config.service;

import com.github.yun531.climate.service.notification.rule.RainForecastRule;
import com.github.yun531.climate.service.notification.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RainForecastRuleConfig {

    @Bean
    public RainForecastComputer rainForecastComputer(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxPoints
    ) {
        // maxPoints(26)를 RainForecast의 maxHourlyHours로 사용
        return new RainForecastComputer(thresholdPop, maxPoints);
    }

    @Bean
    public RainForecastPartsAdjuster rainForecastPartsAdjuster(
            @Value("${notification.max-shift-hours:2}") int maxShiftHours,
            @Value("${notification.window-hours:24}") int windowHours
    ) {
        // windowHours(24)를 horizonHours로 사용
        return new RainForecastPartsAdjuster(maxShiftHours, windowHours);
    }

    @Bean
    public RainForecastRule rainForecastRule(
            SnapshotQueryService snapshotQueryService,
            RainForecastComputer computer,
            RainForecastPartsAdjuster adjuster,
            @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes,
            @Value("${notification.threshold-pop:60}") int thresholdPop
    ) {
        return new RainForecastRule(
                snapshotQueryService,
                computer,
                adjuster,
                recomputeThresholdMinutes,
                thresholdPop
        );
    }
}

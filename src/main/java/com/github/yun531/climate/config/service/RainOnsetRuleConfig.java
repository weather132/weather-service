package com.github.yun531.climate.config.service;


import com.github.yun531.climate.service.notification.rule.RainOnsetChangeRule;
import com.github.yun531.climate.service.notification.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RainOnsetRuleConfig {

    @Bean
    public RainOnsetEventValidAtAdjuster rainOnsetEventValidAtAdjuster(
            @Value("${notification.window-hours:24}") int windowHours
    ) {
        return new RainOnsetEventValidAtAdjuster(windowHours);
    }

    @Bean
    public RainOnsetEventComputer rainOnsetEventComputer(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxPoints
    ) {
        return new RainOnsetEventComputer(thresholdPop, maxPoints);
    }

    @Bean
    public RainOnsetChangeRule rainOnsetChangeRule(
            SnapshotQueryService snapshotQueryService,
            RainOnsetEventValidAtAdjuster adjuster,
            RainOnsetEventComputer computer,
            @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes
    ) {
        return new RainOnsetChangeRule(
                snapshotQueryService,
                adjuster,
                computer,
                recomputeThresholdMinutes
        );
    }
}
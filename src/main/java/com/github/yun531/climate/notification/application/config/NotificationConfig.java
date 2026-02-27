package com.github.yun531.climate.notification.application.config;

import com.github.yun531.climate.notification.application.GenerateAlertsService;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.notification.domain.rule.AlertRule;
import com.github.yun531.climate.notification.domain.rule.RainForecastRule;
import com.github.yun531.climate.notification.domain.rule.RainOnsetChangeRule;
import com.github.yun531.climate.notification.domain.rule.WarningIssuedRule;
import com.github.yun531.climate.notification.domain.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.notification.domain.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.notification.domain.rule.compute.RainForecastComputer;
import com.github.yun531.climate.notification.domain.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.port.WarningIssuedJudgePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NotificationConfig {

    @Bean
    public GenerateAlertsService generateAlertsService(
            List<AlertRule> rules,
            @Value("${notification.max-region-count:3}") int maxRegionCount
    ) {
        return new GenerateAlertsService(rules, maxRegionCount);
    }

    @Bean
    public RainForecastComputer rainForecastComputer(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxPoints
    ) {
        return new RainForecastComputer(thresholdPop, maxPoints);
    }

    @Bean
    public RainOnsetEventComputer rainOnsetEventComputer(
            @Value("${notification.threshold-pop:60}") int thresholdPop,
            @Value("${notification.max-points:26}") int maxPoints
    ) {
        return new RainOnsetEventComputer(thresholdPop, maxPoints);
    }

    @Bean
    public RainForecastPartsAdjuster rainForecastPartsAdjuster(
            @Value("${notification.max-shift-hours:2}") int maxShiftHours,
            @Value("${notification.window-hours:24}") int windowHours
    ) {
        return new RainForecastPartsAdjuster(maxShiftHours, windowHours, 1);
    }

    @Bean
    public RainOnsetEventValidAtAdjuster rainOnsetEventValidAtAdjuster(
            @Value("${notification.window-hours:24}") int windowHours
    ) {
        return new RainOnsetEventValidAtAdjuster(windowHours);
    }

    @Bean
    public AlertRule rainForecastRule(
            PopViewReadPort popViewReadPort,
            RainForecastComputer computer,
            RainForecastPartsAdjuster adjuster,
            @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes,
            @Value("${notification.threshold-pop:60}") int thresholdPop
    ) {
        return new RainForecastRule(
                popViewReadPort,
                computer,
                adjuster,
                recomputeThresholdMinutes,
                thresholdPop
        );
    }

    @Bean
    public AlertRule rainOnsetChangeRule(
            PopViewReadPort popViewReadPort,
            RainOnsetEventValidAtAdjuster windowAdjuster,
            RainOnsetEventComputer computer,
            @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes
    ) {
        return new RainOnsetChangeRule(
                popViewReadPort,
                windowAdjuster,
                computer,
                recomputeThresholdMinutes
        );
    }

    @Bean
    public AlertRule warningIssuedRule(
            WarningStateReadPort warningStateReadPort,
            WarningIssuedJudgePort warningIssuedJudgePort,
            @Value("${notification.warning.cache-ttl-minutes:45}") int cacheTtlMinutes,
            @Value("${notification.warning.since-adjust-minutes:90}") int sinceAdjustMinutes
    ) {
        return new WarningIssuedRule(
                warningStateReadPort, warningIssuedJudgePort, cacheTtlMinutes, sinceAdjustMinutes
        );
    }
}
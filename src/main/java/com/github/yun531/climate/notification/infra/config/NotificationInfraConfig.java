package com.github.yun531.climate.notification.infra.config;

import com.github.yun531.climate.kernel.warning.port.WarningIssuedJudgePort;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.notification.domain.rule.AlertRule;
import com.github.yun531.climate.notification.domain.rule.RainForecastRule;
import com.github.yun531.climate.notification.domain.rule.RainOnsetChangeRule;
import com.github.yun531.climate.notification.domain.rule.WarningIssuedRule;
import com.github.yun531.climate.notification.domain.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.notification.domain.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.notification.domain.rule.compute.RainForecastComputer;
import com.github.yun531.climate.notification.domain.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.notification.infra.decorator.CachedAlertRuleDecorator;
import com.github.yun531.climate.notification.infra.decorator.CachedWarningStateReadPort;
import com.github.yun531.climate.notification.infra.decorator.SinceBasedCachePolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationInfraConfig {

    // ---- helper beans (computer/adjuster) ----

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

    // ---- rules (core) + decorator(caching) ----

    @Bean
    public AlertRule rainForecastRule(
            PopViewReadPort popViewReadPort,
            RainForecastComputer computer,
            RainForecastPartsAdjuster adjuster,
            @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes,
            @Value("${notification.threshold-pop:60}") int thresholdPop
    ) {
        AlertRule core = new RainForecastRule(popViewReadPort, computer, adjuster, thresholdPop);
        return new CachedAlertRuleDecorator(core, new SinceBasedCachePolicy(recomputeThresholdMinutes));
    }

    @Bean
    public AlertRule rainOnsetChangeRule(
            PopViewReadPort popViewReadPort,
            RainOnsetEventValidAtAdjuster windowAdjuster,
            RainOnsetEventComputer computer,
            @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes
    ) {
        AlertRule core = new RainOnsetChangeRule(popViewReadPort, windowAdjuster, computer);
        return new CachedAlertRuleDecorator(core, new SinceBasedCachePolicy(recomputeThresholdMinutes));
    }

    @Bean
    public WarningStateReadPort cachedWarningStateReadPort(
            @Qualifier("jpaWarningStateReadAdapter") WarningStateReadPort delegate,
            @Value("${notification.warning.cache-ttl-minutes:45}") int cacheTtlMinutes
    ) {
        return new CachedWarningStateReadPort(delegate, cacheTtlMinutes);
    }

    @Bean
    public AlertRule warningIssuedRule(
            // WarningIssuedRule 에서만 캐시 포트를 사용
            @Qualifier("cachedWarningStateReadPort") WarningStateReadPort warningStateReadPort,
            WarningIssuedJudgePort warningIssuedJudgePort,
            @Value("${notification.warning.since-adjust-minutes:90}") int sinceAdjustMinutes
    ) {
        return new WarningIssuedRule(warningStateReadPort, warningIssuedJudgePort, sinceAdjustMinutes);
    }
}
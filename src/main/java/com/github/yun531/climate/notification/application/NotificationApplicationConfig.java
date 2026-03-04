package com.github.yun531.climate.notification.application;

import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.compute.RainForecastComputer;
import com.github.yun531.climate.notification.domain.compute.RainOnsetComputer;
import com.github.yun531.climate.notification.domain.compute.WarningIssuedComputer;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationApplicationConfig {

    @Bean
    public GenerateAlertsService generateAlertsService(
            PopViewReadPort popViewReadPort,
            WarningStateReadPort warningStateReadPort,
            RainOnsetComputer rainOnsetComputer,
            RainForecastComputer rainForecastComputer,
            WarningIssuedComputer warningIssuedComputer,
            RainOnsetAdjuster onsetAdjuster,
            RainForecastAdjuster forecastAdjuster,
            @Value("${notification.max-region-count:3}") int maxRegionCount,
            @Value("${notification.warning.default-since-hours:2}") int defaultSinceHours
    ) {
        return new GenerateAlertsService(
                popViewReadPort,
                warningStateReadPort,
                rainOnsetComputer,
                rainForecastComputer,
                warningIssuedComputer,
                onsetAdjuster,
                forecastAdjuster,
                maxRegionCount,
                defaultSinceHours
        );
    }
}
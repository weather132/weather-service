package com.github.yun531.climate.notification.application;

import com.github.yun531.climate.kernel.warning.reader.WarningStateReader;
import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.evaluator.RainForecastEvaluator;
import com.github.yun531.climate.notification.domain.evaluator.RainOnsetEvaluator;
import com.github.yun531.climate.notification.domain.evaluator.WarningIssuedEvaluator;
import com.github.yun531.climate.notification.domain.readmodel.PopViewReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationApplicationConfig {

    @Bean
    public GenerateAlertsService generateAlertsService(
            PopViewReader popViewReader,
            WarningStateReader warningStateReader,
            RainOnsetEvaluator rainOnsetEvaluator,
            RainForecastEvaluator rainForecastEvaluator,
            WarningIssuedEvaluator warningIssuedEvaluator,
            RainOnsetAdjuster onsetAdjuster,
            RainForecastAdjuster forecastAdjuster,
            @Value("${notification.max-region-count:3}") int maxRegionCount,
            @Value("${notification.warning.default-since-hours:2}") int defaultSinceHours
    ) {
        return new GenerateAlertsService(
                popViewReader,
                warningStateReader,
                rainOnsetEvaluator,
                rainForecastEvaluator,
                warningIssuedEvaluator,
                onsetAdjuster,
                forecastAdjuster,
                maxRegionCount,
                defaultSinceHours
        );
    }
}
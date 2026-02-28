package com.github.yun531.climate.notification.application.config;

import com.github.yun531.climate.notification.application.GenerateAlertsService;
import com.github.yun531.climate.notification.domain.rule.AlertRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NotificationApplicationConfig {

    @Bean
    public GenerateAlertsService generateAlertsService(
            List<AlertRule> rules,
            @Value("${notification.max-region-count:3}") int maxRegionCount
    ) {
        return new GenerateAlertsService(rules, maxRegionCount);
    }
}
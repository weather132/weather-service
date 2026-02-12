package com.github.yun531.climate.config.service;

import com.github.yun531.climate.service.notification.NotificationService;
import com.github.yun531.climate.service.notification.rule.AlertRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NotificationConfig {

    @Bean
    public NotificationService notificationService(
            List<AlertRule> rules,
            @Value("${notification.max-region-count:3}") int maxRegionCount
    ) {
        return new NotificationService(rules, maxRegionCount);
    }
}

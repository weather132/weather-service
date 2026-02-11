package com.github.yun531.climate.config.service;


import com.github.yun531.climate.service.notification.rule.WarningIssuedRule;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WarningRuleConfig {

    @Bean
    public WarningIssuedRule warningIssuedRule(
            WarningStateQueryService warningStateQueryService,
            @Value("${notification.warning.cache-ttl-minutes:45}") int ttlMinutes,
            @Value("${notification.warning.since-adjust-minutes:90}") int sinceAdjustMinutes
    ) {
        return new WarningIssuedRule(warningStateQueryService, ttlMinutes, sinceAdjustMinutes);
    }
}
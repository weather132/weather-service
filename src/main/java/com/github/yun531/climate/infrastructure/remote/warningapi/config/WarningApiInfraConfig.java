package com.github.yun531.climate.infrastructure.remote.warningapi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        WarningApiProperties.class
})
public class WarningApiInfraConfig {
}
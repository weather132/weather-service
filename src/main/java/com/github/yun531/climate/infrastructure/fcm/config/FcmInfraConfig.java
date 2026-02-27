package com.github.yun531.climate.infrastructure.fcm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        FcmTriggerProperties.class,
        FirebaseProperties.class
})
public class FcmInfraConfig {
}
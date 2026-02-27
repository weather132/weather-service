package com.github.yun531.climate;

import com.github.yun531.climate.infrastructure.fcm.config.FirebaseProperties;
import com.github.yun531.climate.infrastructure.fcm.config.FcmTriggerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ClimateApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClimateApplication.class, args);
    }
}
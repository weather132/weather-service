package com.github.yun531.climate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClimateApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClimateApplication.class, args);
    }
}
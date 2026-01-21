package com.github.yun531.climate.infra.snapshot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyForecastItem(
        LocalDateTime announceTime,
        LocalDateTime effectiveTime,
        Integer temp,
        Integer pop
) {
}
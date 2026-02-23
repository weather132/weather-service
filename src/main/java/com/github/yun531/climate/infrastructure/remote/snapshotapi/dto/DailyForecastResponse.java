package com.github.yun531.climate.infrastructure.remote.snapshotapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyForecastResponse(
        String regionCode,
        List<DailyForecastItem> forecasts
) {
}
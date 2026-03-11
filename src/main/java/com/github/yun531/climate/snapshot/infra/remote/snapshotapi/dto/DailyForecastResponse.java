package com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyForecastResponse(
        String regionCode,
        @JsonProperty("forecasts")
        List<DailyForecastItem> items
) {
}
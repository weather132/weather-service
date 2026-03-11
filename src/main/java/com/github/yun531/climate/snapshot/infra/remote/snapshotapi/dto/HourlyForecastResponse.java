package com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HourlyForecastResponse(
        LocalDateTime announceTime,
        Integer coordsX,
        Integer coordsY,
        @JsonProperty("gridForecastData")
        List<HourlyForecastItem> items
) {
}
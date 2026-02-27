package com.github.yun531.climate.infrastructure.remote.snapshotapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HourlySnapshotResponse(
        LocalDateTime announceTime,
        Integer coordsX,
        Integer coordsY,
        @JsonProperty("gridForecastData")
        List<GridPoint> gridForecastData
) {
}
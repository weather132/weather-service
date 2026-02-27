package com.github.yun531.climate.infrastructure.remote.snapshotapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GridPoint(
        LocalDateTime effectiveTime,
        Integer pop,
        Integer temp
) {
}
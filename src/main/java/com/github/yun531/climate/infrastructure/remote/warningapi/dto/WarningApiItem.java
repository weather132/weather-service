package com.github.yun531.climate.infrastructure.remote.warningapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WarningApiItem(
        String kind,
        String level,
        LocalDateTime updatedAt
) {}   // 임시
package com.github.yun531.climate.infrastructure.remote.warningapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WarningApiResponse(
        String regionId,
        List<WarningApiItem> items
) {} // 임시
package com.github.yun531.climate.warning.infra.remote.warningapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WarningApiResponse(
        // 임시
) {}
package com.github.yun531.climate.infrastructure.remote.warningapi.api;

import com.github.yun531.climate.infrastructure.remote.warningapi.dto.WarningApiResponse;

public interface WarningApiClient {
    WarningApiResponse fetchLatest(String regionId);
}
package com.github.yun531.climate.warning.infra.remote.warningapi.api;

import com.github.yun531.climate.warning.infra.remote.warningapi.dto.WarningApiResponse;

public interface WarningApiClient {
    WarningApiResponse fetchLatest(String regionId);
}
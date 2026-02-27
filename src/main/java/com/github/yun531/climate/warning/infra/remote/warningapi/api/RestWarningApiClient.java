package com.github.yun531.climate.warning.infra.remote.warningapi.api;

import com.github.yun531.climate.warning.infra.remote.warningapi.dto.WarningApiResponse;
import org.springframework.stereotype.Component;

@Component
public class RestWarningApiClient implements WarningApiClient {

    @Override
    public WarningApiResponse fetchLatest(String regionId) {
        // TODO warningapi 개발 완료 후 RestTemplate/WebClient로 구현
        return null;
    }
}
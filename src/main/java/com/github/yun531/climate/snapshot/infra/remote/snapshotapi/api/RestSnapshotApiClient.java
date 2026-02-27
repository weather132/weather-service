package com.github.yun531.climate.snapshot.infra.remote.snapshotapi.api;

import com.github.yun531.climate.snapshot.infra.config.SnapshotApiProperties;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlySnapshotResponse;
import com.github.yun531.climate.shared.http.UrlQueryUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RestSnapshotApiClient implements SnapshotApiClient {

    private final SnapshotApiProperties props;
    private final RestTemplateBuilder builder;

    private volatile RestTemplate rest;

    private RestTemplate rest() {
        RestTemplate r = this.rest;
        if (r != null) return r;

        r = builder
                .rootUri(props.baseUrl())
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(props.readTimeoutMs()))
                .build();

        this.rest = r;  //캐싱
        return r;
    }

    @Override
    public HourlySnapshotResponse fetchHourly(String regionCode, LocalDateTime announceTime) {
        try {
            String uri = UrlQueryUtil.buildUri(
                    "/hourly/snapshot",
                    Map.of(
                            "regionCode", regionCode,
                            "announceTime", UrlQueryUtil.formatIso(announceTime)
                    )
            );
            return rest().getForObject(uri, HourlySnapshotResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    @Override
    public DailyForecastResponse fetchDaily(String regionCode) {
        try {
            String uri = UrlQueryUtil.buildUri(
                    "/daily/forecast",
                    Map.of("regionCode", regionCode)
            );
            return rest().getForObject(uri, DailyForecastResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }
}
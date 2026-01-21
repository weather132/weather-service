package com.github.yun531.climate.infra.snapshot;

import com.github.yun531.climate.config.snapshot.SnapshotApiProperties;
import com.github.yun531.climate.infra.snapshot.dto.DailyForecastResponse;
import com.github.yun531.climate.infra.snapshot.dto.HourlySnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RestSnapshotApiClient implements SnapshotApiClient {

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SnapshotApiProperties props;
    private final RestTemplateBuilder builder;

    private volatile RestTemplate rest;

    /** RestTemplate를 최초 1회만 생성하고 이후에는 캐시된 인스턴스를 재사용 */
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

    /** URI 생성 */
    @Override
    public HourlySnapshotResponse fetchHourly(String regionCode, LocalDateTime announceTime) {
        try {
            String uri = UriComponentsBuilder.fromPath("/hourly/snapshot")
                    .queryParam("regionCode", regionCode)
                    .queryParam("announceTime", ISO_LOCAL.format(announceTime))
                    .build(true)
                    .toUriString();

            return rest().getForObject(uri, HourlySnapshotResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    @Override
    public DailyForecastResponse fetchDaily(String regionCode) {
        try {
            String uri = UriComponentsBuilder.fromPath("/daily/forecast")
                    .queryParam("regionCode", regionCode)
                    .build(true)
                    .toUriString();

            return rest().getForObject(uri, DailyForecastResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }
}
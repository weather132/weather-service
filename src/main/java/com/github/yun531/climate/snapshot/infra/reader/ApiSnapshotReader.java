package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.api.SnapshotApiClient;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlyForecastResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.mapper.SnapshotApiResponseMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApiSnapshotReader extends CachingSnapshotReader {

    private final SnapshotApiClient client;
    private final PublishSchedulePolicy publishSchedule;
    private final SnapshotApiResponseMapper mapper;

    public ApiSnapshotReader(
            SnapshotCacheProperties cacheProps,
            PublishSchedulePolicy publishSchedule,
            Clock clock,
            SnapshotApiClient client,
            SnapshotApiResponseMapper mapper
    ) {
        super(cacheProps, publishSchedule, clock);
        this.client = client;
        this.publishSchedule = publishSchedule;
        this.mapper = mapper;
    }

    /**
     * snapshot 조회(hourly + daily 조합)해 WeatherSnapshot 으로 변환
     * 새 발표시각으로 점프하면 즉시 stale 판정.
     */
    @Override
    protected CacheEntry<WeatherSnapshot> doFetch(
            SnapshotKey key, LocalDateTime now, LocalDateTime announceTime
    ) {
        String regionId = key.regionId();

        // 시간별 예보 조회
        HourlyForecastResponse hourlyResponse = client.fetchHourly(regionId, announceTime);
        if (hourlyResponse == null || isEmptyItems(hourlyResponse.items())) {
            return null;
        }

        // 일별 예보 조회
        LocalDate baseDate = extractBaseDate(hourlyResponse, announceTime);
        DailyForecastResponse dailyResponse = client.fetchDaily(regionId);
        if (dailyResponse == null || isEmptyItems(dailyResponse.items())) {
            return null;
        }

        // 조립
        WeatherSnapshot snapshot = mapper.toSnapshot(regionId, hourlyResponse, dailyResponse, baseDate);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }

    // =====================================================================
    //  헬퍼
    // =====================================================================

    private boolean isEmptyItems(@Nullable List<?> items) {
        return items == null || items.isEmpty();
    }

    private LocalDate extractBaseDate(HourlyForecastResponse response, LocalDateTime fallback) {
        LocalDateTime t = (response.announceTime() != null) ? response.announceTime() : fallback;
        return t.toLocalDate();
    }
}
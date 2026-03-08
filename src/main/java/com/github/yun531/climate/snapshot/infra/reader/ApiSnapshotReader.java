package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.api.SnapshotApiClient;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.DailyForecastResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.HourlySnapshotResponse;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.mapper.SnapshotApiResponseMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
            SnapshotKey key, LocalDateTime now, LocalDateTime publishTime
    ) {
        String regionId = key.regionId();

        // 발표 데이터 접근 가능 여부
        if (!publishSchedule.isAccessible(now, publishTime)) {
            return emptyCacheEntry(now);
        }

        // 시간별 예보 조회
        HourlySnapshotResponse hourlyResponse = client.fetchHourly(regionId, publishTime);
        if (!hasData(hourlyResponse)) {
            return emptyCacheEntry(now);
        }

        // 일별 예보 조회
        LocalDate baseDate = extractBaseDate(hourlyResponse, publishTime);
        DailyForecastResponse dailyResponse = client.fetchDaily(regionId);

        // 조립
        WeatherSnapshot snapshot = mapper.toSnapshot(regionId, hourlyResponse, dailyResponse, baseDate);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }

    // =====================================================================
    //  헬퍼
    // =====================================================================

    private boolean hasData(@Nullable HourlySnapshotResponse response) {
        return response != null
                && response.gridForecastData() != null
                && !response.gridForecastData().isEmpty();
    }

    private LocalDate extractBaseDate(HourlySnapshotResponse response, LocalDateTime fallback) {
        LocalDateTime t = (response.announceTime() != null) ? response.announceTime() : fallback;
        return t.toLocalDate();
    }
}
package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.KeyCache;
import com.github.yun531.climate.shared.time.TimeUtil;
import com.github.yun531.climate.snapshot.domain.model.SnapKind;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.domain.reader.SnapshotReader;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import org.springframework.lang.Nullable;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * SnapshotReader 공통 캐싱 골격.
 * - 캐시 키 생성, 발표시각 resolve, stale 판정, Clock 기반 시간 취득을 한 곳에서 처리
 */
public abstract class CachingSnapshotReader implements SnapshotReader {

    private final SnapshotCacheProperties cacheProps;
    private final PublishSchedulePolicy publishSchedule;
    private final Clock clock;

    private final KeyCache<WeatherSnapshot> snapshotCache = new KeyCache<>();

    protected CachingSnapshotReader(
            SnapshotCacheProperties cacheProps,
            PublishSchedulePolicy publishSchedule,
            Clock clock
    ) {
        this.cacheProps = cacheProps;
        this.publishSchedule = publishSchedule;
        this.clock = clock;
    }

    @Override
    @Nullable
    public WeatherSnapshot loadCurrent(String regionId) {
        return load(regionId, SnapKind.CURRENT);
    }

    @Override
    @Nullable
    public WeatherSnapshot loadPrevious(String regionId) {
        return load(regionId, SnapKind.PREVIOUS);
    }

    private WeatherSnapshot load(String regionId, SnapKind kind) {
        if (regionId == null || regionId.isBlank() || kind == null) return null;

        LocalDateTime now = now();
        LocalDateTime announceTime = publishSchedule.announceTimeFor(now, kind);
        if (announceTime == null) return null;

        SnapshotKey key = SnapshotKey.of(regionId, kind);

        CacheEntry<WeatherSnapshot> entry = snapshotCache.getOrCompute(
                key.asCacheKey(),
                announceTime,
                cacheProps.recomputeThresholdMinutes(),
                () -> doFetch(key, now, announceTime)
        );

        return (entry == null) ? null : entry.value();
    }

    /**
     * 실제 데이터 조회를 수행한다.
     */
    protected abstract CacheEntry<WeatherSnapshot> doFetch(
            SnapshotKey key, LocalDateTime now, LocalDateTime announceTime);

    private LocalDateTime now() {
        return TimeUtil.truncateToMinutes(LocalDateTime.now(clock));
    }
}
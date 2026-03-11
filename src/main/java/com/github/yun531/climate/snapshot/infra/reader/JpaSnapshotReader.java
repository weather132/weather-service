package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.infra.persistence.entity.SnapshotEntity;
import com.github.yun531.climate.snapshot.infra.persistence.mapper.SnapshotEntityMapper;
import com.github.yun531.climate.snapshot.infra.persistence.repository.SnapshotRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Primary            //todo  로컬 DB 사용해서 JPA 사용중
public class JpaSnapshotReader extends CachingSnapshotReader {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotEntityMapper mapper;

    public JpaSnapshotReader(
            SnapshotCacheProperties cacheProps,
            PublishSchedulePolicy publishSchedule,
            Clock clock,
            SnapshotRepository snapshotRepository,
            SnapshotEntityMapper mapper
    ) {
        super(cacheProps, publishSchedule, clock);
        this.snapshotRepository = snapshotRepository;
        this.mapper = mapper;
    }

    /**
     * DB 에서 Entity를 조회해 WeatherSnapshot 으로 변환한다.
     * 새 발표시각으로 점프하면 즉시 stale 판정.
     */
    @Override
    protected CacheEntry<WeatherSnapshot> doFetch(
            SnapshotKey key, LocalDateTime now, LocalDateTime announceTime
    ) {
        SnapshotEntity entity = snapshotRepository.findBySnapIdAndRegionId(
                key.asSnapId(), key.regionId()
        );

        if (entity == null) return emptyCacheEntry(now);

        WeatherSnapshot snapshot = mapper.toSnapshot(entity);
        return new CacheEntry<>(snapshot, snapshot.reportTime());
    }
}
package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingSnapshotReaderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 22, 5, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private static final SnapshotCacheProperties CACHE_PROPS =
            new SnapshotCacheProperties(180, 165);
    private static final PublishSchedulePolicy PUBLISH_SCHEDULE =
            new PublishSchedulePolicy(10);

    private AtomicInteger fetchCount;
    private CachingSnapshotReader reader;

    @BeforeEach
    void setUp() {
        fetchCount = new AtomicInteger(0);

        reader = readerWith((key, now, announceTime) -> {
            WeatherSnapshot snap = new WeatherSnapshot(
                    key.regionId(), announceTime, List.of(), List.of());

            return new CacheEntry<>(snap, announceTime);
        });
    }

    // --- 정상 경로 ---

    @Test
    @DisplayName("loadCurrent -> doFetch 호출되어 WeatherSnapshot 반환")
    void loadCurrent_callsDoFetch() {
        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        assertThat(snap).isNotNull();
        assertThat(snap.regionId()).isEqualTo("11B10101");
        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 key 반복 호출 -> 캐시 히트로 doFetch 1회만")
    void repeatedCall_cachesResult() {
        reader.loadCurrent("11B10101");
        reader.loadCurrent("11B10101");

        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 regionId -> 각각 doFetch 호출")
    void differentRegionIds_separateFetch() {
        reader.loadCurrent("11B10101");
        reader.loadCurrent("11B20201");

        assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("loadCurrent + loadPrevious -> 다른 SnapKind로 별도 캐시")
    void currentAndPrevious_separateCache() {
        reader.loadCurrent("11B10101");
        reader.loadPrevious("11B10101");

        assertThat(fetchCount.get()).isEqualTo(2);
    }

    // --- 입력 가드 ---

    @Test
    @DisplayName("null regionId -> doFetch 미호출, null 반환")
    void nullRegionId_returnsNull() {
        assertThat(reader.loadCurrent(null)).isNull();
        assertThat(fetchCount.get()).isZero();
    }

    @Test
    @DisplayName("blank regionId -> doFetch 미호출, null 반환")
    void blankRegionId_returnsNull() {
        assertThat(reader.loadCurrent("")).isNull();
        assertThat(fetchCount.get()).isZero();
    }

    // --- doFetch null 방어 ---

    @Test
    @DisplayName("doFetch가 null 반환 -> NPE 없이 null 반환")
    void doFetch_returnsNull_safelyReturnsNull() {
        CachingSnapshotReader nullReader = readerWith((key, now, announceTime) -> null);

        assertThat(nullReader.loadCurrent("11B10101")).isNull();
        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("doFetch가 emptyCacheEntry 반환 -> value null -> null 반환")
    void doFetch_returnsEmptyCacheEntry_returnsNull() {
        CachingSnapshotReader emptyReader = readerWith(
                (key, now, announceTime) -> new CacheEntry<>(null, now));

        assertThat(emptyReader.loadCurrent("11B10101")).isNull();
        assertThat(fetchCount.get()).isEqualTo(1);
    }

    // --- 헬퍼 ---

    private CachingSnapshotReader readerWith(DoFetchLambda doFetch) {
        return new CachingSnapshotReader(CACHE_PROPS, PUBLISH_SCHEDULE, FIXED_CLOCK) {
            @Override
            protected CacheEntry<WeatherSnapshot> doFetch(
                    SnapshotKey key, LocalDateTime now, LocalDateTime announceTime
            ) {
                fetchCount.incrementAndGet();
                return doFetch.fetch(key, now, announceTime);
            }
        };
    }

    @FunctionalInterface
    private interface DoFetchLambda {
        CacheEntry<WeatherSnapshot> fetch(
                SnapshotKey key, LocalDateTime now, LocalDateTime announceTime);
    }
}
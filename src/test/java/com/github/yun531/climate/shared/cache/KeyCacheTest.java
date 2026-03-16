package com.github.yun531.climate.shared.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyCacheTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("최초 조회 시 loader가 호출되어 값이 캐싱된다")
    void firstAccess_loaderCalled() {
        KeyCache<String> cache = new KeyCache<>();
        AtomicInteger calls = new AtomicInteger();

        CacheEntry<String> entry = cache.getOrCompute("key1", T0, 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("value1", T0);
        });

        assertThat(entry.value()).isEqualTo("value1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("tolerance 내 재조회 시 캐시 히트 (loader 미호출)")
    void withinTolerance_cacheHit() {
        KeyCache<String> cache = new KeyCache<>();
        AtomicInteger calls = new AtomicInteger();

        cache.getOrCompute("k", T0, 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("v", T0);
        });

        // tolerance 60분 이내 -> stale 아님
        cache.getOrCompute("k", T0.plusMinutes(30), 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("v2", T0.plusMinutes(30));
        });

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("tolerance 초과 시 stale 판정 -> loader 재호출")
    void exceedsTolerance_recomputed() {
        KeyCache<String> cache = new KeyCache<>();
        AtomicInteger calls = new AtomicInteger();

        cache.getOrCompute("k", T0, 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("v1", T0);
        });

        // tolerance 60분 초과
        CacheEntry<String> entry = cache.getOrCompute("k", T0.plusMinutes(61), 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("v2", T0.plusMinutes(61));
        });

        assertThat(calls.get()).isEqualTo(2);
        assertThat(entry.value()).isEqualTo("v2");
    }

    @Test
    @DisplayName("referenceTime이 null 이면 항상 stale -> loader 호출")
    void nullReferenceTime_alwaysStale() {
        KeyCache<String> cache = new KeyCache<>();
        AtomicInteger calls = new AtomicInteger();

        cache.getOrCompute("k", T0, 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("v1", T0);
        });

        cache.getOrCompute("k", null, 60, () -> {
            calls.incrementAndGet();
            return new CacheEntry<>("v2", T0);
        });

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("loader가 null 이면 IllegalArgumentException")
    void nullLoader_throws() {
        KeyCache<String> cache = new KeyCache<>();

        assertThatThrownBy(() -> cache.getOrCompute("k", T0, 60, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

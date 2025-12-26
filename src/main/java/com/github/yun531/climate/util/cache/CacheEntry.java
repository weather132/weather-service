package com.github.yun531.climate.util.cache;

import java.time.LocalDateTime;

/** 캐시 값 + 계산 시각을 묶어두는 공통 레코드 */
public record CacheEntry<T>(
        T value,
        LocalDateTime computedAt
) {}

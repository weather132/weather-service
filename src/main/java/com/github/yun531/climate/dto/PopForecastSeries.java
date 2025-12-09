package com.github.yun531.climate.dto;

import io.micrometer.common.lang.Nullable;

/** 예보 요약용 구조체 (시간대 PopSeries24, 일자별 PopDailySeries7) */
public record PopForecastSeries(
        @Nullable PopSeries24 hourly,
        @Nullable PopDailySeries7 daily
) {}

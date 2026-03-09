package com.github.yun531.climate.forecast.domain.readmodel;

import java.time.LocalDateTime;

public record ForecastHourlyEntry(
        LocalDateTime validAt,  // 발효시간
        Integer temp,
        Integer pop
) {}
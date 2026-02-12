package com.github.yun531.climate.service.forecast.model;

import java.time.LocalDateTime;

public record HourlyPoint(
        LocalDateTime validAt,      // 발효시간
        Integer temp,
        Integer pop
) {}

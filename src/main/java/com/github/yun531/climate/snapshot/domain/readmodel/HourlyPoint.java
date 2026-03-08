package com.github.yun531.climate.snapshot.domain.readmodel;

import java.time.LocalDateTime;

public record HourlyPoint(
        LocalDateTime validAt,
        Integer temp,
        Integer pop
) {}
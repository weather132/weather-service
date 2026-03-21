package com.github.yun531.climate.snapshot.domain.readmodel;

import java.time.LocalDateTime;

public record HourlyPoint(
        LocalDateTime effectiveTime,
        Integer temp,
        Integer pop
) {}
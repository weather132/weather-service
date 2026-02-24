package com.github.yun531.climate.kernel.snapshot.readmodel;

import java.time.LocalDateTime;

public record SnapshotHourlyPoint(
        LocalDateTime validAt,
        Integer temp,
        Integer pop
) {}
package com.github.yun531.climate.kernel.snapshot.readmodel;

public record DailyPoint(
        int dayOffset,       // 0~6
        Integer minTemp,
        Integer maxTemp,
        Integer amPop,
        Integer pmPop
) {}
package com.github.yun531.climate.dto;

public record DailyPoint(
        int dayOffset,       // 0~6
        Integer maxTemp,
        Integer minTemp,
        Integer amPop,
        Integer pmPop
) {}

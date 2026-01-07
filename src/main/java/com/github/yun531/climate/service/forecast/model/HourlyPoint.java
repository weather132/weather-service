package com.github.yun531.climate.service.forecast.model;

public record HourlyPoint(
        int hourOffset,      // 1~25      \
        Integer temp,
        Integer pop
) {}

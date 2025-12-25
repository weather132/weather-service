package com.github.yun531.climate.service.forecast.model;

public record HourlyPoint(
        int hourOffset,      // 1~25      //todo: 25시간 까지 제공받는걸로 바꿈
        Integer temp,
        Integer pop
) {}

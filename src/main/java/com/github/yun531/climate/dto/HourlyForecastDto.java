package com.github.yun531.climate.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시간대별 예보 DTO
 * - 기준 시간: reportTime
 * - 각 항목: 기준 시각으로부터 hourOffset시간 후의 온도/POP
 */
public record HourlyForecastDto(
        int regionId,
        LocalDateTime reportTime,
        List<HourlyPoint> hours
) {}
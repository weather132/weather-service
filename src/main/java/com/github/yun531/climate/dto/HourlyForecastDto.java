package com.github.yun531.climate.dto;

import com.github.yun531.climate.service.forecast.model.HourlyPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시간대별 예보 DTO
 * - 기준 시간: reportTime
 * - 각 항목: hours (validAt, temp, pop)
 */
public record

HourlyForecastDto(
        String regionId,
        LocalDateTime reportTime,
        List<HourlyPoint> hours
) {}
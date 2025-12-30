package com.github.yun531.climate.dto;

import com.github.yun531.climate.service.forecast.model.DailyPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일자별 AM/PM 예보 DTO
 * - 기준 날짜: reportTime.toLocalDate()
 * - 각 항목: dayOffset일 후의 AM/PM 온도/POP
 */
public record DailyForecastDto(
        String regionId,
        LocalDateTime reportTime,
        List<DailyPoint> days
) { }
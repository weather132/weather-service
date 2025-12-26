package com.github.yun531.climate.controller;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.AppForecastService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final AppForecastService appForecastService;

    @GetMapping("/hourly")
    @Operation(
            summary = "시간대별 예보 조회",
            description = "시간대별(1-24시간) 예보 조회, 3시간 마다 갱신."
    )
    public HourlyForecastDto getHourlyForecast(@RequestParam int regionId) {
        return appForecastService.getHourlyForecast(regionId);
    }

    @GetMapping("/daily")
    @Operation(
            summary = "일자별 AM/PM 예보 조회",
            description = "일자별(0-6일차) AM/PM 예보 조회"
    )
    public DailyForecastDto getDailyForecast(@RequestParam int regionId) {
        return appForecastService.getDailyForecast(regionId);
    }
}
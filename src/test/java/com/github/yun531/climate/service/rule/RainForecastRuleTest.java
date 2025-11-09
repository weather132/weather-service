package com.github.yun531.climate.service.rule;


import com.github.yun531.climate.service.ClimateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RainForecastRuleTest {

    @Mock
    ClimateService climateService;

    @Test
    void dayParts_ampm7x2_생성검증() {
        // D+0 (오전 70, 오후 10), D+1 (오전 10, 오후 70) D+2 (오전 65, 오후 65), D+3 (오전 0, 오후 0), D+4 오전 80
        Byte[][] ampm = new Byte[][]{
                {(byte)70, (byte)10}, {(byte)10, (byte)70}, {(byte)65, (byte)65},
                {(byte)0, (byte)0}, {(byte)80, (byte)0}, {(byte)0, (byte)0}, {(byte)0, (byte)0}
        };
        int[] hourly = new int[24];

        when(climateService.loadForecastSeries(1L, 1L))
                .thenReturn(new ClimateService.ForecastSeries(hourly, ampm));

        RainForecastRule rule = new RainForecastRule(climateService);

        var events = rule.evaluate(List.of(1L), Instant.parse("2025-11-04T00:00:00Z"));
        assertThat(events).hasSize(1);

        Map<String, Object> payload = events.get(0).payload();
        @SuppressWarnings("unchecked")
        List<String> dayParts = (List<String>) payload.get("dayParts");

        assertThat(dayParts).contains("오늘 오전");
        assertThat(dayParts).contains("내일 오후");
        assertThat(dayParts).contains("모레 오전");
        assertThat(dayParts).contains("모레 오후");
        assertThat(dayParts).contains("4일 후 오전");

//        dayParts.forEach(System.out::println);
    }

    @Test
    void hourlyParts_오늘_내일_라벨과_시각형태를_반환한다() {
        // 연속 POP≥60 구간을 일부 포함(임계치=60)
        int[] hourly = new int[24];
        // 오늘: 2~4시 비
        hourly[2] = 60; hourly[3] = 60; hourly[4] = 60;
        // 내일 라벨은 현재 시각에 따라 달라질 수 있으므로 두 번째 블록은 생략

        Byte[][] ampm = new Byte[][]{
                {(byte)0,(byte)0},{(byte)0,(byte)0},{(byte)0,(byte)0},
                {(byte)0,(byte)0},{(byte)0,(byte)0},{(byte)0,(byte)0},{(byte)0,(byte)0}
        };

        when(climateService.loadForecastSeries(9L, 1L))
                .thenReturn(new ClimateService.ForecastSeries(hourly, ampm));

        RainForecastRule rule = new RainForecastRule(climateService);
        var events = rule.evaluate(List.of(9L), Instant.now());

        assertThat(events).hasSize(1);
        @SuppressWarnings("unchecked")
        List<String> hourlyParts = (List<String>) events.get(0).payload().get("hourlyParts");

        // 내용이 비어있지 않고, "오늘" 또는 "내일"과 "시" 표기가 포함되는지만 확인
        assertThat(hourlyParts).isNotEmpty();
        assertThat(String.join(" ", hourlyParts)).contains("시");
        assertThat(String.join(" ", hourlyParts)).containsAnyOf("오늘", "내일");

//        hourlyParts.forEach(System.out::println);
    }
}
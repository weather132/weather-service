package com.github.yun531.climate.service.notification.model.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RainForecastPayload(
        String srcRule,
        List<RainInterval> hourlyParts,   // 비 구간(절대시각)
        List<DailyRainFlags> dayParts        // size 7 고정 (index = dayOffset)
) implements AlertPayload {

    public RainForecastPayload {
        hourlyParts = (hourlyParts == null) ? List.of() : List.copyOf(hourlyParts);

        // dayParts는 항상 7개로 정규화(부족하면 false 패딩, 초과하면 절단)
        List<DailyRainFlags> src = (dayParts == null) ? List.of() : dayParts;
        List<DailyRainFlags> fixed = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            DailyRainFlags v = (i < src.size()) ? src.get(i) : null;
            fixed.add(v == null ? new DailyRainFlags(false, false) : v);
        }
        dayParts = List.copyOf(fixed);
    }

    @Override
    public Map<String, String> toFcmData() {
        // 구조가 커서 FCM에는 요약만 싣는 기본값
        return Map.of(
                "_srcRule", srcRule == null ? "" : srcRule,
                "hourlyPartsCount", String.valueOf(hourlyParts == null ? 0 : hourlyParts.size())
        );
    }
}
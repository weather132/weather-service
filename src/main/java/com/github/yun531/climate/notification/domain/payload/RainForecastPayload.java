package com.github.yun531.climate.notification.domain.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RainForecastPayload(
        String srcRule,
        List<RainInterval> hourlyParts,
        List<DailyRainFlags> dayParts
) implements AlertPayload {

    /** 비 시간 구간 (절대 시각) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RainInterval(LocalDateTime start, LocalDateTime end) {

        public RainInterval {
            if (start != null && end != null && start.isAfter(end)) {
                LocalDateTime tmp = start;
                start = end;
                end = tmp;
            }
        }
    }

    /** 일별 오전/오후 비 플래그 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DailyRainFlags(boolean rainAm, boolean rainPm) {}

    public RainForecastPayload {
        hourlyParts = (hourlyParts == null) ? List.of() : List.copyOf(hourlyParts);
        dayParts = (dayParts == null) ? List.of() : List.copyOf(dayParts);
    }

    @Override
    public Map<String, String> toFcmData() {
        return Map.of(
                "_srcRule", srcRule == null ? "" : srcRule,
                "hourlyPartsCount", String.valueOf(hourlyParts == null ? 0 : hourlyParts.size())
        );
    }
}
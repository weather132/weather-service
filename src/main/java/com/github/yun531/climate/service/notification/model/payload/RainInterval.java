package com.github.yun531.climate.service.notification.model.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RainInterval(LocalDateTime start, LocalDateTime end) {

    public RainInterval {
        // 뒤집혀 들어오면 정규화
        if (start != null && end != null && start.isAfter(end)) {
            LocalDateTime tmp = start;
            start = end;
            end = tmp;
        }
    }

    /** window로 클리핑. 겹치지 않으면 null */
    public RainInterval clamp(LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (start == null || end == null) return null;

        if (end.isBefore(windowStart)) return null;
        if (start.isAfter(windowEnd)) return null;

        LocalDateTime ns = start.isBefore(windowStart) ? windowStart : start;
        LocalDateTime ne = end.isAfter(windowEnd) ? windowEnd : end;

        if (ne.isBefore(ns)) return null;
        return new RainInterval(ns, ne);
    }
}
package com.github.yun531.climate.notification.domain.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 강수 이벤트 페이로드.
 * - validAt: 비가 예보된 절대 시각
 * - pop: 해당 시각의 강수확률
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RainOnsetPayload(
        AlertTypeEnum type,
        LocalDateTime validAt,
        int pop
) implements AlertPayload {

    @Override
    public Map<String, String> toFcmData() {
        return Map.of(
                "_source", type == null ? "" : type.source(),
                "validAt", validAt == null ? "" : validAt.toString(),
                "pop", String.valueOf(pop)
        );
    }
}
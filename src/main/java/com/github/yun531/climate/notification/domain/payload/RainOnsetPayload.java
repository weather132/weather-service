package com.github.yun531.climate.notification.domain.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RainOnsetPayload(
        String srcRule,
        LocalDateTime validAt,
        int pop
) implements AlertPayload, ValidAtPayload {

    @Override
    public Map<String, String> toFcmData() {
        return Map.of(
                "_srcRule", srcRule == null ? "" : srcRule,
                "validAt", validAt == null ? "" : validAt.toString(),
                "pop", String.valueOf(pop)
        );
    }
}
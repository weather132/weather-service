package com.github.yun531.climate.service.notification.util;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import io.micrometer.common.lang.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class AlertPayloads {
    private AlertPayloads() {}

    @Nullable
    public static LocalDateTime readLocalDateTime(@Nullable AlertEvent e, String key) {
        if (e == null) return null;
        return readLocalDateTime(e.payload(), key);
    }

    @Nullable
    public static LocalDateTime readLocalDateTime(@Nullable Map<String, Object> payload, String key) {
        if (payload == null) return null;

        Object v = payload.get(key);
        if (v instanceof LocalDateTime t) return t;

        if (v instanceof String s) {
            try {
                return LocalDateTime.parse(s); // "2026-01-15T21:00:00"
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }
}
package com.github.yun531.climate.notification.domain.model;

import com.github.yun531.climate.notification.domain.payload.AlertPayload;

import java.time.LocalDateTime;

public record AlertEvent(
        AlertTypeEnum type,
        String regionId,
        LocalDateTime occurredAt,
        AlertPayload payload  // 시간대, 경보단계 등 부가정보
) {}

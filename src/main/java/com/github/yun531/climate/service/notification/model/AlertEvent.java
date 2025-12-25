package com.github.yun531.climate.service.notification.model;

import java.time.LocalDateTime;
import java.util.Map;

public record AlertEvent(
        AlertTypeEnum type,
        int regionId,
        LocalDateTime occurredAt,
        Map<String, Object> payload // 시간대, 단계 등 부가정보
) {}

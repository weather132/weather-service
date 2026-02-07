package com.github.yun531.climate.service.query.dto;

import com.github.yun531.climate.entity.WarningState;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;

import java.time.LocalDateTime;

public record WarningStateDto(
        String regionId,            // 지역 코드
        WarningKind kind,           // 호우 / 폭염 / 강풍 / 태풍 ...
        WarningLevel level,         // 예비특보 / 주의보 / 경보
        LocalDateTime updatedAt     // 특보가 발효되거나 갱신된 시각
) {
    public static WarningStateDto from(WarningState ws) {
        if (ws == null) return null;
        return new WarningStateDto(
                ws.getRegionId(),
                ws.getKind(),
                ws.getLevel(),
                ws.getUpdatedAt()
        );
    }
}
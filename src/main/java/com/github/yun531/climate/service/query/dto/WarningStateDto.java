package com.github.yun531.climate.service.query.dto;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;
import com.github.yun531.climate.entity.WarningState;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WarningStateDto {

    private String regionId;          // 지역 코드
    private WarningKind kind;       // 호우 / 폭염 / 강풍 / 태풍 ...
    private WarningLevel level;     // 예비특보 / 주의보 / 경보
    private LocalDateTime updatedAt;      // 특보가 발효되거나 갱신된 시각

    public static WarningStateDto from(WarningState ws) {
        return WarningStateDto.builder()
                .regionId(ws.getRegionId())
                .kind(ws.getKind())
                .level(ws.getLevel())
                .updatedAt(ws.getUpdatedAt())
                .build();
    }
}

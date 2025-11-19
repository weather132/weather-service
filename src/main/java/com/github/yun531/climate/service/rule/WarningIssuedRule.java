package com.github.yun531.climate.service.rule;


import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.dto.WarningStateDto;
import com.github.yun531.climate.service.WarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WarningIssuedRule implements AlertRule {

    private final WarningService warningService;

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.WARNING_ISSUED;
    }

    @Override
    public List<AlertEvent> evaluate(List<Integer> regionIds, LocalDateTime since) {
        // 지역별 모든 kind에 대해 각 kind의 최신 1건을 가져온다
        Map<Integer, Map<WarningKind, WarningStateDto>> latest = warningService.findLatestByRegionAndKind(regionIds);
        List<AlertEvent> out = new ArrayList<>();

        LocalDateTime adjustedSince = (since == null) ? null : since.minusMinutes(90);    // 특보 발효 시각과의 시차 보정용

        for (int regionId : regionIds) {
            Map<WarningKind, WarningStateDto> byKind = latest.get(regionId);
            if (byKind == null || byKind.isEmpty()) continue;

            for (Map.Entry<WarningKind, WarningStateDto> e : byKind.entrySet()) {
                WarningStateDto w = e.getValue();
                if (w == null) continue;

                boolean isNew = (adjustedSince == null) || warningService.isNewlyIssuedSince(w, adjustedSince);
                if (!isNew) continue;

                LocalDateTime occurredAt = (w.getUpdatedAt() != null) ? w.getUpdatedAt() : LocalDateTime.now();

                out.add(new AlertEvent(
                        AlertTypeEnum.WARNING_ISSUED,
                        regionId,
                        occurredAt,
                        Map.of(
                                "_srcRule", "WarningIssuedRule",
                                "kind",  w.getKind(),   // WarningKind enum
                                "level", w.getLevel()   // WarningLevel enum
                        )
                ));
            }
        }
        return out;
    }
}

package com.github.yun531.climate.service.rule;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
import com.github.yun531.climate.dto.WarningStateDto;
import com.github.yun531.climate.service.WarningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarningIssuedRuleTest {

    @Mock
    WarningService warningService;

    @Test
    void since_null이면_모두_이벤트_발생() {
        WarningIssuedRule rule = new WarningIssuedRule(warningService);
        int regionId = 100;

        var dtoRain = new WarningStateDto(regionId, WarningKind.RAIN, WarningLevel.WARNING, LocalDateTime.parse("2025-11-04T07:00:00"));
        var dtoHeat = new WarningStateDto(regionId, WarningKind.HEAT, WarningLevel.ADVISORY, LocalDateTime.parse("2025-11-04T06:00:00"));

        when(warningService.findLatestByRegionAndKind(List.of(regionId)))
                .thenReturn(Map.of(regionId, Map.of(
                        WarningKind.RAIN, dtoRain,
                        WarningKind.HEAT, dtoHeat
                )));

        // since = null → isNewlyIssuedSince 호출 결과와 무관하게 포함
        var events = rule.evaluate(List.of(regionId), null);

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.type() == AlertTypeEnum.WARNING_ISSUED);
        assertThat(events).extracting(e -> e.payload().get("kind"))
                .containsExactlyInAnyOrder(WarningKind.RAIN, WarningKind.HEAT);
    }

    @Test
    void since_지정시_isNewlyIssuedSince_true인_것만_포함() {
        WarningIssuedRule rule = new WarningIssuedRule(warningService);
        int regionId = 200;
        LocalDateTime since = LocalDateTime.parse("2025-11-04T06:30:00");
        LocalDateTime adjustedSince = since.minusMinutes(90);

        var dtoRain = new WarningStateDto(regionId, WarningKind.RAIN, WarningLevel.WARNING, LocalDateTime.parse("2025-11-04T07:00:00")); // 포함
        var dtoHeat = new WarningStateDto(regionId, WarningKind.HEAT, WarningLevel.ADVISORY, LocalDateTime.parse("2025-11-04T06:00:00")); // 제외

        when(warningService.findLatestByRegionAndKind(List.of(regionId)))
                .thenReturn(Map.of(regionId, Map.of(
                        WarningKind.RAIN, dtoRain,
                        WarningKind.HEAT, dtoHeat
                )));

        when(warningService.isNewlyIssuedSince(dtoRain, adjustedSince)).thenReturn(true);
        when(warningService.isNewlyIssuedSince(dtoHeat, adjustedSince)).thenReturn(false);

        var events = rule.evaluate(List.of(regionId), since);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload().get("kind")).isEqualTo(WarningKind.RAIN);
        assertThat(events.get(0).payload().get("level")).isEqualTo(WarningLevel.WARNING);
    }
}
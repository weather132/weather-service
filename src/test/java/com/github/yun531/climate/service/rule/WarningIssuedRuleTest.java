package com.github.yun531.climate.service.rule;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
import com.github.yun531.climate.dto.WarningStateDto;
import com.github.yun531.climate.service.WarningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarningIssuedRuleTest {

    @Mock
    WarningService warningService;

    @Test
    void since_null이면_모두_이벤트_발생() {
        WarningIssuedRule rule = new WarningIssuedRule(warningService);
        Long R = 100L;

        var dtoRain = new WarningStateDto(R, WarningKind.RAIN, WarningLevel.WARNING, Instant.parse("2025-11-04T07:00:00Z"));
        var dtoHeat = new WarningStateDto(R, WarningKind.HEAT, WarningLevel.ADVISORY, Instant.parse("2025-11-04T06:00:00Z"));

        when(warningService.findLatestByRegionAndKind(List.of(R)))
                .thenReturn(Map.of(R, Map.of(
                        WarningKind.RAIN, dtoRain,
                        WarningKind.HEAT, dtoHeat
                )));

        // since = null → isNewlyIssuedSince 호출 결과와 무관하게 포함
        var events = rule.evaluate(List.of(R), null);

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.type() == AlertTypeEnum.WARNING_ISSUED);
        assertThat(events).extracting(e -> e.payload().get("kind"))
                .containsExactlyInAnyOrder(WarningKind.RAIN, WarningKind.HEAT);
    }

    @Test
    void since_지정시_isNewlyIssuedSince_true인_것만_포함() {
        WarningIssuedRule rule = new WarningIssuedRule(warningService);
        Long R = 200L;
        Instant since = Instant.parse("2025-11-04T06:30:00Z");
        Instant adjustedSince = since.minus(90, ChronoUnit.MINUTES);

        var dtoRain = new WarningStateDto(R, WarningKind.RAIN, WarningLevel.WARNING, Instant.parse("2025-11-04T07:00:00Z")); // 포함
        var dtoHeat = new WarningStateDto(R, WarningKind.HEAT, WarningLevel.ADVISORY, Instant.parse("2025-11-04T06:00:00Z")); // 제외

        when(warningService.findLatestByRegionAndKind(List.of(R)))
                .thenReturn(Map.of(R, Map.of(
                        WarningKind.RAIN, dtoRain,
                        WarningKind.HEAT, dtoHeat
                )));

        when(warningService.isNewlyIssuedSince(dtoRain, adjustedSince)).thenReturn(true);
        when(warningService.isNewlyIssuedSince(dtoHeat, adjustedSince)).thenReturn(false);

        var events = rule.evaluate(List.of(R), since);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload().get("kind")).isEqualTo(WarningKind.RAIN);
        assertThat(events.get(0).payload().get("level")).isEqualTo(WarningLevel.WARNING);
    }
}
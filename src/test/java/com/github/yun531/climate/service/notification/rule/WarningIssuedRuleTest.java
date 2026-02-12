package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;
import com.github.yun531.climate.service.notification.model.payload.WarningIssuedPayload;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarningIssuedRuleTest {

    @Mock
    WarningStateQueryService warningStateQueryService;

    @Test
    void since_null이면_모두_이벤트_발생() {
        WarningIssuedRule rule = new WarningIssuedRule(warningStateQueryService, 45, 90);
        String regionId = "100";

        var dtoRain = new WarningStateDto(
                regionId,
                WarningKind.RAIN,
                WarningLevel.WARNING,
                LocalDateTime.parse("2025-11-04T07:00:00")
        );
        var dtoHeat = new WarningStateDto(
                regionId,
                WarningKind.HEAT,
                WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T06:00:00")
        );

        when(warningStateQueryService.findLatestByRegionAndKind(List.of(regionId)))
                .thenReturn(Map.of(
                        regionId,
                        Map.of(
                                WarningKind.RAIN, dtoRain,
                                WarningKind.HEAT, dtoHeat
                        )
                ));

        NotificationRequest req = new NotificationRequest(
                List.of(regionId),
                null,
                EnumSet.of(AlertTypeEnum.WARNING_ISSUED),
                null,
                null
        );

        var events = rule.evaluate(req);

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.type() == AlertTypeEnum.WARNING_ISSUED);

        assertThat(events)
                .extracting(e -> ((WarningIssuedPayload) e.payload()).kind())
                .containsExactlyInAnyOrder(WarningKind.RAIN, WarningKind.HEAT);

        verify(warningStateQueryService, never()).isNewlyIssuedSince(any(), any());
    }

    @Test
    void since_지정시_isNewlyIssuedSince_true인_것만_포함() {
        WarningIssuedRule rule = new WarningIssuedRule(warningStateQueryService, 45, 90);
        String regionId = "200";
        LocalDateTime since = LocalDateTime.parse("2025-11-04T06:30:00");
        LocalDateTime adjustedSince = since.minusMinutes(90);

        var dtoRain = new WarningStateDto(
                regionId,
                WarningKind.RAIN,
                WarningLevel.WARNING,
                LocalDateTime.parse("2025-11-04T07:00:00")
        );
        var dtoHeat = new WarningStateDto(
                regionId,
                WarningKind.HEAT,
                WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T06:00:00")
        );

        when(warningStateQueryService.findLatestByRegionAndKind(List.of(regionId)))
                .thenReturn(Map.of(
                        regionId,
                        Map.of(
                                WarningKind.RAIN, dtoRain,
                                WarningKind.HEAT, dtoHeat
                        )
                ));

        when(warningStateQueryService.isNewlyIssuedSince(dtoRain, adjustedSince)).thenReturn(true);
        when(warningStateQueryService.isNewlyIssuedSince(dtoHeat, adjustedSince)).thenReturn(false);

        NotificationRequest req = new NotificationRequest(
                List.of(regionId),
                since,
                EnumSet.of(AlertTypeEnum.WARNING_ISSUED),
                null,
                null
        );

        var events = rule.evaluate(req);

        assertThat(events).hasSize(1);

        WarningIssuedPayload p = (WarningIssuedPayload) events.get(0).payload();
        assertThat(p.kind()).isEqualTo(WarningKind.RAIN);
        assertThat(p.level()).isEqualTo(WarningLevel.WARNING);

        verify(warningStateQueryService, times(1)).isNewlyIssuedSince(dtoRain, adjustedSince);
        verify(warningStateQueryService, times(1)).isNewlyIssuedSince(dtoHeat, adjustedSince);
    }


    @Test
    void filterWarningKinds가_있으면_해당_kind만_이벤트_생성() {
        WarningIssuedRule rule = new WarningIssuedRule(warningStateQueryService, 45, 90);
        String regionId = "300";

        var dtoRain = new WarningStateDto(
                regionId,
                WarningKind.RAIN,
                WarningLevel.WARNING,
                LocalDateTime.parse("2025-11-04T07:00:00")
        );
        var dtoHeat = new WarningStateDto(
                regionId,
                WarningKind.HEAT,
                WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T07:30:00")
        );

        when(warningStateQueryService.findLatestByRegionAndKind(List.of(regionId)))
                .thenReturn(Map.of(
                        regionId,
                        Map.of(
                                WarningKind.RAIN, dtoRain,
                                WarningKind.HEAT, dtoHeat
                        )
                ));

        NotificationRequest req = new NotificationRequest(
                List.of(regionId),
                null,
                EnumSet.of(AlertTypeEnum.WARNING_ISSUED),
                Set.of(WarningKind.RAIN),
                null
        );

        var events = rule.evaluate(req);

        assertThat(events).hasSize(1);
        WarningIssuedPayload p = (WarningIssuedPayload) events.get(0).payload();
        assertThat(p.kind()).isEqualTo(WarningKind.RAIN);
    }
}
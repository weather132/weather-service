package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
        // given
        WarningIssuedRule rule = new WarningIssuedRule(warningStateQueryService);
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

        // since = null → adjustedSince = null → isNewlyIssuedSince 결과와 상관없이 모두 포함
        NotificationRequest req = new NotificationRequest(
                List.of(regionId),
                null,       // since
                null,       // enabledTypes (룰 내부에서는 사용 안 함)
                null,       // filterWarningKinds
                null        // rainHourLimit
        );

        // when
        var events = rule.evaluate(req);

        // then
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.type() == AlertTypeEnum.WARNING_ISSUED);
        assertThat(events)
                .extracting(e -> e.payload().get("kind"))
                .containsExactlyInAnyOrder(WarningKind.RAIN, WarningKind.HEAT);

        // since == null이면 adjustedSince == null 이므로 isNewlyIssuedSince 호출 안 되는 것이 자연스럽다
        verify(warningStateQueryService, never()).isNewlyIssuedSince(any(), any());
    }

    @Test
    void since_지정시_isNewlyIssuedSince_true인_것만_포함() {
        // given
        WarningIssuedRule rule = new WarningIssuedRule(warningStateQueryService);
        String regionId = "200";
        LocalDateTime since = LocalDateTime.parse("2025-11-04T06:30:00");
        LocalDateTime adjustedSince = since.minusMinutes(90); // WarningIssuedRule.adjustSince 로 보정되는 값

        var dtoRain = new WarningStateDto(
                regionId,
                WarningKind.RAIN,
                WarningLevel.WARNING,
                LocalDateTime.parse("2025-11-04T07:00:00")  // 포함
        );
        var dtoHeat = new WarningStateDto(
                regionId,
                WarningKind.HEAT,
                WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T06:00:00")  // 제외
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
                since,          // since
                null,           // enabledTypes
                null,           // filterWarningKinds (kind 필터링은 이 테스트에서 사용 안 함)
                null            // rainHourLimit
        );

        // when
        var events = rule.evaluate(req);

        // then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload().get("kind")).isEqualTo(WarningKind.RAIN);
        assertThat(events.get(0).payload().get("level")).isEqualTo(WarningLevel.WARNING);

        // isNewlyIssuedSince 가 보정된 기준 시각으로 호출되는지 검증
        verify(warningStateQueryService, times(1)).isNewlyIssuedSince(dtoRain, adjustedSince);
        verify(warningStateQueryService, times(1)).isNewlyIssuedSince(dtoHeat, adjustedSince);
    }

    @Test
    void filterWarningKinds가_있으면_해당_kind만_이벤트_생성() {
        // 옵션: 필터링까지 검증하고 싶다면 이렇게 하나 더 둘 수 있음
        WarningIssuedRule rule = new WarningIssuedRule(warningStateQueryService);
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

        // since = null → adjustedSince = null → 모두 "새로운" 것으로 취급
        NotificationRequest req = new NotificationRequest(
                List.of(regionId),
                null,
                null,
                Set.of(WarningKind.RAIN),   // 이 kind만 남도록 필터
                null
        );

        var events = rule.evaluate(req);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload().get("kind")).isEqualTo(WarningKind.RAIN);
    }
}
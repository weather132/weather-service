package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;
import com.github.yun531.climate.service.notification.model.payload.*;
import com.github.yun531.climate.service.notification.rule.RainForecastRule;
import com.github.yun531.climate.service.notification.rule.RainOnsetChangeRule;
import com.github.yun531.climate.service.notification.rule.WarningIssuedRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private RainOnsetChangeRule rainRule;      // supports() -> RAIN_ONSET
    @Mock
    private WarningIssuedRule warnRule;        // supports() -> WARNING_ISSUED
    @Mock
    private RainForecastRule forecastRule;     // supports() -> RAIN_FORECAST

    private NotificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(rainRule.supports()).thenReturn(AlertTypeEnum.RAIN_ONSET);
        lenient().when(warnRule.supports()).thenReturn(AlertTypeEnum.WARNING_ISSUED);
        lenient().when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        service = new NotificationService(List.of(rainRule, warnRule, forecastRule));
    }

    /**
     * 방어 로직
     */

    @Test
    @DisplayName("request == null이면 빈 리스트를 반환하고 어떤 룰도 실행되지 않는다")
    void null_request_returns_empty_and_does_not_call_rules() {
        List<AlertEvent> out = service.generate(null);

        assertThat(out).isEmpty();
        verifyNoInteractions(rainRule, warnRule, forecastRule);
    }

    @Test
    @DisplayName("regionIds가 null이면 빈 리스트를 반환하고 룰이 실행되지 않는다")
    void null_regionIds_returns_empty_and_does_not_call_rules() {
        NotificationRequest request = new NotificationRequest(
                null,
                LocalDateTime.parse("2025-11-04T05:00:00"),
                EnumSet.of(AlertTypeEnum.RAIN_ONSET),
                null,
                null
        );

        List<AlertEvent> out = service.generate(request);

        assertThat(out).isEmpty();
        verifyNoInteractions(rainRule, warnRule, forecastRule);
    }

    @Test
    @DisplayName("regionIds가 empty면 빈 리스트를 반환하고 룰이 실행되지 않는다")
    void empty_regionIds_returns_empty_and_does_not_call_rules() {
        NotificationRequest request = new NotificationRequest(
                List.of(),
                LocalDateTime.parse("2025-11-04T05:00:00"),
                EnumSet.of(AlertTypeEnum.RAIN_ONSET),
                null,
                null
        );

        List<AlertEvent> out = service.generate(request);

        assertThat(out).isEmpty();
        verifyNoInteractions(rainRule, warnRule, forecastRule);
    }

    /**
     * enabledTypes 정책 동작
     * - null/empty => noneOf => 아무 룰도 실행 안 함
     */

    @Test
    @DisplayName("enabledTypes가 null이면 아무 룰도 실행되지 않고 빈 리스트를 반환한다")
    void none_when_enabledTypes_null() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        String regionId = "1";

        NotificationRequest request = req(List.of(regionId), t, null);

        // when
        List<AlertEvent> out = service.generate(request);

        // then
        assertThat(out).isEmpty();
        verifyNoInteractions(rainRule, warnRule, forecastRule);
    }

    @Test
    @DisplayName("enabledTypes가 empty면 아무 룰도 실행되지 않고 빈 리스트를 반환한다")
    void none_when_enabledTypes_empty() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        String regionId = "1";

        NotificationRequest request = req(List.of(regionId), t, Collections.emptySet());

        // when
        List<AlertEvent> out = service.generate(request);

        // then
        assertThat(out).isEmpty();
        verifyNoInteractions(rainRule, warnRule, forecastRule);
    }

    @Test
    @DisplayName("enabledTypes에 RAIN_ONSET, WARNING_ISSUED를 주면 두 룰 모두 실행된다")
    void enabled_both_rules() {
        String regionId01 = "1", regionId02 = "2";
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);
        NotificationRequest request = req(List.of(regionId01), null, enabled);

        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2025-11-04T06:00:00");

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                rainEvent(regionId01, t1, 5, 70)
        ));
        when(warnRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                warningEvent(regionId02, t2, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(2);
        assertThat(out)
                .extracting(AlertEvent::type)
                .containsExactlyInAnyOrder(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);

        verify(rainRule, times(1)).evaluate(any(NotificationRequest.class));
        verify(warnRule, times(1)).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    /**
     * deduplicate / region cap / sort / since 전달
     */

    @Test
    @DisplayName("deduplicate: 동일 (type|region|occurredAt|payload) 이벤트는 한 번만 남는다")
    void deduplicate_removes_duplicates() {
        String regionId = "1";
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        NotificationRequest request = req(List.of(regionId), t1, enabled);

        AlertEvent dup1 = rainEvent("1", t1, 5, 70);
        AlertEvent dup2 = rainEvent("1", t1, 5, 70);
        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(dup1, dup2));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        AlertEvent e = out.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo("1");
        assertThat(e.occurredAt()).isEqualTo(t1);
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_is_capped_to_three() {
        List<String> regionIds = List.of("10", "11", "12", "13");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        NotificationRequest request = req(regionIds, t1, enabled);

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of());

        service.generate(request);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());
        List<String> passed = captor.getValue().regionIds();
        assertThat(passed).containsExactly("10", "11", "12");
    }

    @Test
    @DisplayName("정렬: 타입 → 지역 → 발생시각 순으로 정렬된다")
    void sort_by_type_region_then_time() {
        var regionIds = List.of("1", "2");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);
        LocalDateTime t0 = LocalDateTime.parse("2025-11-04T04:00:00");
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2025-11-04T06:00:00");
        NotificationRequest request = req(regionIds, t0, enabled);

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                rainEvent("2", t1, 5, 70),
                rainEvent("1", t2, 6, 80)
        ));
        when(warnRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                warningEvent("1", t1, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(3);

        AlertEvent e0 = out.get(0);
        AlertEvent e1 = out.get(1);
        AlertEvent e2 = out.get(2);

        assertThat(e0.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e0.regionId()).isEqualTo("1");

        assertThat(e1.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e1.regionId()).isEqualTo("2");

        assertThat(e2.type()).isEqualTo(AlertTypeEnum.WARNING_ISSUED);
        assertThat(e2.regionId()).isEqualTo("1");
    }

    @Test
    @DisplayName("since 값이 정규화 후 룰 evaluate의 NotificationRequest.since 로 전달된다")
    void since_is_forwarded_to_rules() {
        var regionIds = List.of("1");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        LocalDateTime since = LocalDateTime.parse("2025-11-04T05:55:00");
        NotificationRequest request = req(regionIds, since, enabled);

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of());

        service.generate(request);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule).evaluate(captor.capture());
        assertThat(captor.getValue().since()).isEqualTo(since);
    }

    /**
     *  RAIN_FORECAST 관련
     */

    @Test
    @DisplayName("RAIN_FORECAST: 룰이 만든 payload(hourlyParts/dayParts)를 그대로 전달한다 (hourlyParts는 validAt 구간)")
    void forecast_payload_is_preserved() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");

        // given: 타입 payload 생성
        RainForecastPayload givenPayload = new RainForecastPayload(
                "RainForecastRule",
                List.of(new RainInterval(
                        LocalDateTime.parse("2026-01-14T09:00:00"),
                        LocalDateTime.parse("2026-01-14T12:00:00")
                )),
                List.of(
                        new DailyRainFlags(true, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false)
                )
        );

        when(forecastRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, "1", t, givenPayload)
        ));

        var regionIds = List.of("1");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        LocalDateTime since = LocalDateTime.parse("2025-11-04T04:00:00");
        NotificationRequest request = req(regionIds, since, enabled);

        // when
        List<AlertEvent> out = service.generate(request);

        // then
        assertThat(out).hasSize(1);
        AlertEvent e = out.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_FORECAST);
        assertThat(e.regionId()).isEqualTo("1");

        assertThat(e.payload()).isInstanceOf(RainForecastPayload.class);
        RainForecastPayload payload = (RainForecastPayload) e.payload();

        // payload가 "그대로" 전달되는지: record면 equals로도 가능
        assertThat(payload).isEqualTo(givenPayload);

        // 필드별 추가 검증
        assertThat(payload.hourlyParts()).containsExactly(
                new RainInterval(
                        LocalDateTime.parse("2026-01-14T09:00:00"),
                        LocalDateTime.parse("2026-01-14T12:00:00")
                )
        );

        assertThat(payload.dayParts()).hasSize(7);
        assertThat(payload.dayParts().get(0)).isEqualTo(new DailyRainFlags(true, false));

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("RAIN_FORECAST: 지역 3개 입력 시 각 지역별 이벤트가 그대로 전달된다 (hourlyParts는 validAt 구간)")
    void forecast_three_regions_payload_and_regions() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");

        RainForecastPayload p1 = new RainForecastPayload(
                "RainForecastRule",
                List.of(new RainInterval(
                        LocalDateTime.parse("2026-01-14T09:00:00"),
                        LocalDateTime.parse("2026-01-14T12:00:00")
                )),
                List.of(
                        new DailyRainFlags(true, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false)
                )
        );

        RainForecastPayload p2 = new RainForecastPayload(
                "RainForecastRule",
                List.of(new RainInterval(
                        LocalDateTime.parse("2026-01-14T15:00:00"),
                        LocalDateTime.parse("2026-01-14T15:00:00")
                )),
                List.of(
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false)
                )
        );

        RainForecastPayload p3 = new RainForecastPayload(
                "RainForecastRule",
                List.of(),
                List.of(
                        new DailyRainFlags(false, true),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false),
                        new DailyRainFlags(false, false)
                )
        );

        when(forecastRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, "1", t, p1),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, "2", t, p2),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, "3", t, p3)
        ));

        var regionIds = List.of("1", "2", "3");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        LocalDateTime since = LocalDateTime.parse("2025-11-04T04:00:00");
        NotificationRequest request = req(regionIds, since, enabled);

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(3);
        assertThat(out).extracting(AlertEvent::regionId).containsExactly("1", "2", "3");

        RainForecastPayload out1 = (RainForecastPayload) out.get(0).payload();
        RainForecastPayload out2 = (RainForecastPayload) out.get(1).payload();
        RainForecastPayload out3 = (RainForecastPayload) out.get(2).payload();

        assertThat(out1).isEqualTo(p1);
        assertThat(out2).isEqualTo(p2);
        assertThat(out3).isEqualTo(p3);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(forecastRule, times(1)).evaluate(captor.capture());
        assertThat(captor.getValue().regionIds()).containsExactly("1", "2", "3");

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
    }

    /**
     * filterKinds / rainHourLimit 분기
     */

    @Test
    @DisplayName("filterWarningKinds가 있으면 NotificationRequest에 담겨 WARNING_ISSUED 룰로 전달된다")
    void filter_kinds_is_forwarded_in_request() {
        var regionIds = List.of("1", "2");
        LocalDateTime since = LocalDateTime.parse("2025-11-04T05:00:00");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.WARNING_ISSUED);
        Set<WarningKind> kinds = EnumSet.of(WarningKind.RAIN);

        NotificationRequest request = reqWithKinds(regionIds, since, enabled, kinds);

        when(warnRule.evaluate(any(NotificationRequest.class)))
                .thenReturn(List.of(
                        warningEvent("1", since, WarningKind.RAIN, WarningLevel.WARNING)
                ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        AlertEvent e = out.get(0);

        assertThat(e.type()).isEqualTo(AlertTypeEnum.WARNING_ISSUED);

        // payload 검증: 타입 캐스팅
        assertThat(e.payload()).isInstanceOf(WarningIssuedPayload.class);
        WarningIssuedPayload payload = (WarningIssuedPayload) e.payload();
        assertThat(payload.kind()).isEqualTo(WarningKind.RAIN);
        assertThat(payload.level()).isEqualTo(WarningLevel.WARNING);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(warnRule, times(1)).evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly("1", "2");
        assertThat(passed.filterWarningKinds()).containsExactly(WarningKind.RAIN);
        assertThat(passed.since()).isEqualTo(since);

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("rainHourLimit가 있으면 NotificationRequest에 담겨 RAIN_ONSET 룰로 전달되고 region은 최대 3개로 제한된다")
    void rainHourLimit_is_forwarded_in_request_and_regions_capped() {
        var regionIds = List.of("10", "11", "12", "13");
        LocalDateTime since = LocalDateTime.parse("2025-11-04T05:00:00");
        int limitHour = 12;

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        NotificationRequest request = reqWithHourLimit(regionIds, since, enabled, limitHour);

        when(rainRule.evaluate(any(NotificationRequest.class)))
                .thenReturn(List.of(
                        rainEvent("10", since, 9, 80)
                ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly("10", "11", "12");
        assertThat(passed.since()).isEqualTo(since);
        assertThat(passed.rainHourLimit()).isEqualTo(limitHour);

        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    /* helpers: AlertEvent 생성 */

    private static AlertEvent rainEvent(String regionId, LocalDateTime occurredAt, int hour, int pop) {
        LocalDateTime base = occurredAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        LocalDateTime validAt = base.plusHours(hour);

        RainOnsetPayload payload = new RainOnsetPayload(
                "RainOnsetChangeRule",
                validAt,
                pop
        );

        return new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, occurredAt, payload);
    }

    private static AlertEvent warningEvent(String regionId, LocalDateTime t, WarningKind kind, WarningLevel level) {
        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                t,
                new WarningIssuedPayload("WarningIssuedRule", kind, level)
        );
    }

    /* helpers: NotificationRequest 생성 */

    private NotificationRequest req(List<String> regionIds,
                                    LocalDateTime since,
                                    Set<AlertTypeEnum> enabled) {
        return new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );
    }

    private NotificationRequest reqWithKinds(List<String> regionIds,
                                             LocalDateTime since,
                                             Set<AlertTypeEnum> enabled,
                                             Set<WarningKind> kinds) {
        return new NotificationRequest(
                regionIds,
                since,
                enabled,
                kinds,
                null
        );
    }

    private NotificationRequest reqWithHourLimit(List<String> regionIds,
                                                 LocalDateTime since,
                                                 Set<AlertTypeEnum> enabled,
                                                 int limitHour) {
        return new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                limitHour
        );
    }
}
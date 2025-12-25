package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
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
     * enabledTypes 기본/정상 동작
     */

    @Test
    @DisplayName("기본값: enabledTypes가 null이면 RAIN_ONSET만 실행된다")
    void default_selects_only_rain_rule_when_null() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        int regionId = 1;

        NotificationRequest request = req(List.of(regionId), t, null);

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                rainEvent(regionId, t, 5, 70)
        ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(out.get(0).regionId()).isEqualTo(1);

        verify(rainRule, times(1)).evaluate(any(NotificationRequest.class));
        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("기본값: enabledTypes가 empty이면 RAIN_ONSET만 실행된다")
    void default_selects_only_rain_rule_when_empty() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        int regionId = 1;

        NotificationRequest request = req(List.of(regionId), t, Collections.emptySet());

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                rainEvent(regionId, t, 5, 70)
        ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);

        verify(rainRule, times(1)).evaluate(any(NotificationRequest.class));
        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("enabledTypes에 RAIN_ONSET, WARNING_ISSUED를 주면 두 룰 모두 실행된다")
    void enabled_both_rules() {
        int regionId01 = 1, regionId02 = 2;
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
        int regionId = 1;
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        NotificationRequest request = req(List.of(regionId), t1, enabled);

        AlertEvent dup1 = rainEvent(1, t1, 5, 70);
        AlertEvent dup2 = rainEvent(1, t1, 5, 70);
        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(dup1, dup2));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        AlertEvent e = out.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo(1);
        assertThat(e.occurredAt()).isEqualTo(t1);
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_is_capped_to_three() {
        List<Integer> regionIds = List.of(10, 11, 12, 13);
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        NotificationRequest request = req(regionIds, t1, enabled);

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of());

        service.generate(request);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());
        List<Integer> passed = captor.getValue().regionIds();
        assertThat(passed).containsExactly(10, 11, 12);
    }

    @Test
    @DisplayName("정렬: 타입 → 지역 → 타입명 → 발생시각 순으로 정렬된다")
    void sort_by_region_type_then_time() {
        var regionIds = List.of(1, 2);
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);
        LocalDateTime t0 = LocalDateTime.parse("2025-11-04T04:00:00");
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2025-11-04T06:00:00");
        NotificationRequest request = req(regionIds, t0, enabled);

        when(rainRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                rainEvent(2, t1, 5, 70),
                rainEvent(1, t2, 6, 80)
        ));
        when(warnRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                warningEvent(1, t1, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(3);

        AlertEvent e0 = out.get(0);
        AlertEvent e1 = out.get(1);
        AlertEvent e2 = out.get(2);

        assertThat(e0.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e0.regionId()).isEqualTo(1);

        assertThat(e1.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e1.regionId()).isEqualTo(2);

        assertThat(e2.type()).isEqualTo(AlertTypeEnum.WARNING_ISSUED);
        assertThat(e2.regionId()).isEqualTo(1);
    }

    @Test
    @DisplayName("since 값이 정규화 후 룰 evaluate의 NotificationRequest.since 로 전달된다")
    void since_is_forwarded_to_rules() {
        var regionIds = List.of(1);
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
    @DisplayName("RAIN_FORECAST: 룰이 만든 payload(hourlyParts/dayParts)를 그대로 전달한다")
    void forecast_payload_is_preserved() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");

        Map<String, Object> payload = new HashMap<>();
        payload.put("hourlyParts", List.of(List.of(9, 12)));
        payload.put("dayParts",   List.of(List.of(1, 0)));

        when(forecastRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 1, t, payload)
        ));

        var regionIds = List.of(1);
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T04:00:00");
        NotificationRequest request = req(regionIds, t1, enabled);

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        AlertEvent e = out.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_FORECAST);
        assertThat(e.regionId()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<List<Integer>> hourly = (List<List<Integer>>) e.payload().get("hourlyParts");
        @SuppressWarnings("unchecked")
        List<List<Integer>> day = (List<List<Integer>>) e.payload().get("dayParts");

        assertThat(hourly).containsExactly(List.of(9, 12));
        assertThat(day).containsExactly(List.of(1, 0));

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("RAIN_FORECAST: 지역 3개 입력 시 각 지역별 이벤트가 그대로 전달된다")
    void forecast_three_regions_payload_and_regions() {
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");

        Map<String, Object> p1 = new HashMap<>();
        p1.put("hourlyParts", List.of(List.of(9, 12)));
        p1.put("dayParts",   List.of(List.of(1, 0)));

        Map<String, Object> p2 = new HashMap<>();
        p2.put("hourlyParts", List.of(List.of(15, 15)));
        p2.put("dayParts",   List.of());

        Map<String, Object> p3 = new HashMap<>();
        p3.put("hourlyParts", List.of());
        p3.put("dayParts",   List.of(List.of(0, 1)));

        when(forecastRule.evaluate(any(NotificationRequest.class))).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 1, t, p1),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 2, t, p2),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 3, t, p3)
        ));

        var regionIds = List.of(1, 2, 3);
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T04:00:00");
        NotificationRequest request = req(regionIds, t1, enabled);

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(3);
        assertThat(out)
                .extracting(AlertEvent::regionId)
                .containsExactly(1, 2, 3);

        @SuppressWarnings("unchecked")
        List<List<Integer>> hourly1 = (List<List<Integer>>) out.get(0).payload().get("hourlyParts");
        @SuppressWarnings("unchecked")
        List<List<Integer>> day1 = (List<List<Integer>>) out.get(0).payload().get("dayParts");

        @SuppressWarnings("unchecked")
        List<List<Integer>> hourly2 = (List<List<Integer>>) out.get(1).payload().get("hourlyParts");
        @SuppressWarnings("unchecked")
        List<List<Integer>> day2 = (List<List<Integer>>) out.get(1).payload().get("dayParts");

        @SuppressWarnings("unchecked")
        List<List<Integer>> hourly3 = (List<List<Integer>>) out.get(2).payload().get("hourlyParts");
        @SuppressWarnings("unchecked")
        List<List<Integer>> day3 = (List<List<Integer>>) out.get(2).payload().get("dayParts");

        assertThat(hourly1).containsExactly(List.of(9, 12));
        assertThat(day1).containsExactly(List.of(1, 0));

        assertThat(hourly2).containsExactly(List.of(15, 15));
        assertThat(day2).isEmpty();

        assertThat(hourly3).isEmpty();
        assertThat(day3).containsExactly(List.of(0, 1));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(forecastRule, times(1)).evaluate(captor.capture());
        assertThat(captor.getValue().regionIds()).containsExactly(1, 2, 3);

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
    }

    /**
     * filterKinds / rainHourLimit 분기
     */

    @Test
    @DisplayName("filterWarningKinds가 있으면 NotificationRequest에 담겨 WARNING_ISSUED 룰로 전달된다")
    void filter_kinds_is_forwarded_in_request() {
        var regionIds = List.of(1, 2);
        LocalDateTime since = LocalDateTime.parse("2025-11-04T05:00:00");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.WARNING_ISSUED);
        Set<WarningKind> kinds = EnumSet.of(WarningKind.RAIN);

        NotificationRequest request = reqWithKinds(regionIds, since, enabled, kinds);

        when(warnRule.evaluate(any(NotificationRequest.class)))
                .thenReturn(List.of(
                        warningEvent(1, since, WarningKind.RAIN, WarningLevel.WARNING)
                ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(AlertTypeEnum.WARNING_ISSUED);
        assertThat(out.get(0).payload().get("kind")).isEqualTo(WarningKind.RAIN);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(warnRule, times(1)).evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly(1, 2);
        assertThat(passed.filterWarningKinds()).containsExactly(WarningKind.RAIN);
        assertThat(passed.since()).isEqualTo(since);

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("rainHourLimit가 있으면 NotificationRequest에 담겨 RAIN_ONSET 룰로 전달되고 region은 최대 3개로 제한된다")
    void rainHourLimit_is_forwarded_in_request_and_regions_capped() {
        var regionIds = List.of(10, 11, 12, 13);
        LocalDateTime since = LocalDateTime.parse("2025-11-04T05:00:00");
        int limitHour = 12;

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        NotificationRequest request = reqWithHourLimit(regionIds, since, enabled, limitHour);

        when(rainRule.evaluate(any(NotificationRequest.class)))
                .thenReturn(List.of(
                        rainEvent(10, since, 9, 80)
                ));

        List<AlertEvent> out = service.generate(request);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly(10, 11, 12);
        assertThat(passed.since()).isEqualTo(since);
        assertThat(passed.rainHourLimit()).isEqualTo(limitHour);

        verify(warnRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }


    /** helpers: AlertEvent 생성 */

    private static AlertEvent rainEvent(int regionId, LocalDateTime t, int hour, int pop) {
        return new AlertEvent(
                AlertTypeEnum.RAIN_ONSET,
                regionId,
                t,
                Map.of("hour", hour, "pop", pop)
        );
    }

    private static AlertEvent warningEvent(int regionId, LocalDateTime t, WarningKind kind, WarningLevel level) {
        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                t,
                Map.of("kind", kind, "level", level)
        );
    }

    /** helpers: NotificationRequest 생성 */

    private NotificationRequest req(List<Integer> regionIds,
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

    private NotificationRequest reqWithKinds(List<Integer> regionIds,
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

    private NotificationRequest reqWithHourLimit(List<Integer> regionIds,
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
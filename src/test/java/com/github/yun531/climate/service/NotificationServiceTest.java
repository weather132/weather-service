package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.WarningKind;
import com.github.yun531.climate.dto.WarningLevel;
import com.github.yun531.climate.service.rule.AlertEvent;
import com.github.yun531.climate.service.rule.AlertRule;
import com.github.yun531.climate.service.rule.AlertTypeEnum;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private AlertRule rainRule;        // supports() -> RAIN_ONSET
    @Mock
    private AlertRule warnRule;        // supports() -> WARNING_ISSUED
    @Mock
    private AlertRule forecastRule;    // supports() -> RAIN_FORECAST

    private NotificationService service;

    @BeforeEach
    void setUp() {
        // 룰 supports() 고정
        lenient().when(rainRule.supports()).thenReturn(AlertTypeEnum.RAIN_ONSET);
        lenient().when(warnRule.supports()).thenReturn(AlertTypeEnum.WARNING_ISSUED);
        lenient().when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        // SUT
        service = new NotificationService(List.of(rainRule, warnRule, forecastRule));
    }

    @Test
    @DisplayName("기본값: enabledTypes가 null/empty면 RAIN_ONSET만 실행된다")
    void default_selects_only_rain_rule() {
        // given
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(1, t, 5, 70)
        ));

        // when (enabledTypes null -> 디폴트는 RAIN_ONSET만)
        List<AlertEvent> out = service.generate(List.of(1), null, t);

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(out.get(0).regionId()).isEqualTo(1);

        verify(rainRule, times(1)).evaluate(anyList(), any());
        // WARNING_ISSUED 룰은 필터에서 제외되어 호출되지 않음
        verify(warnRule, never()).evaluate(anyList(), any());
    }

    @Test
    @DisplayName("enabledTypes에 RAIN_ONSET, WARNING_ISSUED를 주면 두 룰 모두 실행된다")
    void enabled_both_rules() {
        // given
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2025-11-04T06:00:00");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(1, t1, 5, 70)
        ));
        when(warnRule.evaluate(anyList(), any())).thenReturn(List.of(
                warningEvent(2, t2, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);
        List<AlertEvent> out = service.generate(List.of(1, 2), enabled, t1);

        // then
        assertThat(out).hasSize(2);
        assertThat(out)
                .extracting(AlertEvent::type)
                .containsExactlyInAnyOrder(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);

        verify(rainRule, times(1)).evaluate(anyList(), any());
        verify(warnRule, times(1)).evaluate(anyList(), any());
    }

    @Test
    @DisplayName("receiveWarnings = true이면 WARNING_ISSUED 룰도 함께 실행된다")
    void generate_with_receiveWarnings_true_runs_warning_rule() {
        // given
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(1, t, 5, 70)
        ));
        when(warnRule.evaluate(anyList(), any())).thenReturn(List.of(
                warningEvent(1, t, WarningKind.RAIN, WarningLevel.ADVISORY)
        ));

        // when (since=null로 호출 → NotificationService 내부에서 now 사용)
        List<AlertEvent> out = service.generate(List.of(1), true, null);

        // then
        assertThat(out).hasSize(2);
        assertThat(out)
                .extracting(AlertEvent::type)
                .containsExactlyInAnyOrder(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);

        verify(rainRule, times(1)).evaluate(anyList(), any());
        verify(warnRule, times(1)).evaluate(anyList(), any());
    }

    @Test
    @DisplayName("deduplicate: 동일 (type|region|occurredAt) 이벤트는 한 번만 남는다")
    void deduplicate_removes_duplicates() {
        // given
        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");
        AlertEvent dup1 = rainEvent(1, t, 5, 70);
        AlertEvent dup2 = rainEvent(1, t, 5, 70); // 같은 키(type|region|t)

        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(dup1, dup2));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        List<AlertEvent> out = service.generate(List.of(1), enabled, t);

        // then
        assertThat(out).hasSize(1);
        AlertEvent e = out.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo(1);
        assertThat(e.occurredAt()).isEqualTo(t);
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_is_capped_to_three() {
        // given: rainRule만 활성화, evaluate는 빈 목록 반환
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of());
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);

        // when
        List<Integer> input = List.of(10, 11, 12, 13); // 4개 입력
        service.generate(input, enabled, LocalDateTime.parse("2025-11-04T05:00:00"));

        // then: 전달된 regionIds는 앞 3개만
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        verify(rainRule, times(1)).evaluate(captor.capture(), any());
        List<Integer> passed = captor.getValue();
        assertThat(passed).containsExactly(10, 11, 12);
    }

    @Test
    @DisplayName("정렬: 타입 → 지역 → 타입명 → 발생시각 순으로 정렬된다")
    void sort_by_region_type_then_time() {
        // given
        LocalDateTime t1 = LocalDateTime.parse("2025-11-04T05:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2025-11-04T06:00:00");

        // region 2에 비 시작(t1), region 1에 비 시작(t2), region 1에 특보(t1)
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(2, t1, 5, 70),
                rainEvent(1, t2, 6, 80)
        ));
        when(warnRule.evaluate(anyList(), any())).thenReturn(List.of(
                warningEvent(1, t1, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);

        // when
        List<AlertEvent> out = service.generate(List.of(1, 2), enabled, LocalDateTime.parse("2025-11-04T04:00:00"));

        // then
        assertThat(out).hasSize(3);

        // 정렬 규칙: type(ordinal) → regionId → typeName → occurredAt
        // 예상 순서:
        //   1) RAIN_ONSET, region=1, t2
        //   2) RAIN_ONSET, region=2, t1
        //   3) WARNING_ISSUED, region=1, t1

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
    @DisplayName("since 값이 룰 evaluate 로 그대로 전달된다")
    void since_is_forwarded_to_rules() {
        LocalDateTime since = LocalDateTime.parse("2025-11-04T05:55:00");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of());
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);

        // when
        service.generate(List.of(1), enabled, since);

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(rainRule).evaluate(anyList(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(since);
    }

    @Test
    @DisplayName("RAIN_FORECAST: 룰이 만든 payload(hourlyParts/dayParts)를 그대로 전달한다")
    void forecast_payload_is_preserved() {
        AlertRule forecastRule = mock(AlertRule.class);
        when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");

        Map<String, Object> payload = new HashMap<>();
        // 시간대: 09~12시 → [9, 12]
        payload.put("hourlyParts", List.of(List.of(9, 12)));
        // 일자 플래그: 예시로 D+0 오전만 비 온다고 가정 → [1,0]
        payload.put("dayParts", List.of(List.of(1, 0)));

        when(forecastRule.evaluate(anyList(), any())).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 1, t, payload)
        ));

        NotificationService svc = new NotificationService(List.of(rainRule, warnRule, forecastRule));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        List<AlertEvent> out = svc.generate(List.of(1), enabled, LocalDateTime.parse("2025-11-04T04:00:00"));

        // then
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
    }

    @Test
    @DisplayName("RAIN_FORECAST: 지역 3개 입력 시 각 지역별 이벤트가 그대로 전달된다")
    void forecast_three_regions_payload_and_regions() {
        // given
        AlertRule forecastRule = mock(AlertRule.class);
        when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        LocalDateTime t = LocalDateTime.parse("2025-11-04T05:00:00");

        Map<String, Object> p1 = new HashMap<>();
        p1.put("hourlyParts", List.of(List.of(9, 12)));       // 09~12시 비
        p1.put("dayParts",   List.of(List.of(1, 0)));         // 오늘 오전만 비

        Map<String, Object> p2 = new HashMap<>();
        p2.put("hourlyParts", List.of(List.of(15, 15)));      // 15시 한 번만 비
        p2.put("dayParts",   List.of());                      // 일자 플래그 없음(또는 전부 0)

        Map<String, Object> p3 = new HashMap<>();
        p3.put("hourlyParts", List.of());                     // 시간대 정보 없음
        p3.put("dayParts",   List.of(List.of(0, 1)));         // 오늘 오후만 비

        when(forecastRule.evaluate(anyList(), any())).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 1, t, p1),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 2, t, p2),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 3, t, p3)
        ));

        // forecastRule만 활성화
        NotificationService svc = new NotificationService(List.of(forecastRule));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        List<AlertEvent> out = svc.generate(List.of(1, 2, 3), enabled, LocalDateTime.parse("2025-11-04T04:00:00"));

        // then: 3개 지역 각각 한 이벤트
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

        // 전달된 regionIds가 정확히 [1,2,3]인지 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        verify(forecastRule, times(1)).evaluate(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(1, 2, 3);
    }

    // ---- helpers ----
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
}
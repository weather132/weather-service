package com.github.yun531.climate.service;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
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

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationGeneratorServiceTest {

    @Mock
    private AlertRule rainRule;   // supports() -> RAIN_ONSET
    @Mock
    private AlertRule warnRule;   // supports() -> WARNING_ISSUED@Mock
    @Mock
    private AlertRule forecastRule;   // supports() -> RAIN_FORECAST

    private NotificationGeneratorService service;

    @BeforeEach
    void setUp() {
        // 룰 supports() 고정
        lenient().when(rainRule.supports()).thenReturn(AlertTypeEnum.RAIN_ONSET);
        lenient().when(warnRule.supports()).thenReturn(AlertTypeEnum.WARNING_ISSUED);
        lenient().when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        // SUT
        service = new NotificationGeneratorService(List.of(rainRule, warnRule, forecastRule));
    }


    @Test
    @DisplayName("기본값: enabledTypes가 null/empty면 RAIN_ONSET만 실행된다")
    void default_selects_only_rain_rule() {
        // given
        Instant t = Instant.parse("2025-11-04T05:00:00Z");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(1L, t, 5, 70)
        ));

        // when (enabledTypes null -> 디폴트는 RAIN_ONSET만)
        List<String> out = service.generate(List.of(1L), null, t, Locale.KOREA);

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).contains("비 시작");     // RAIN_ONSET 포맷
        verify(rainRule, times(1)).evaluate(anyList(), any());
        // WARNING_ISSUED 룰은 필터에서 제외되어 호출되지 않음
        verify(warnRule, never()).evaluate(anyList(), any());
    }

    @Test
    @DisplayName("enabledTypes에 RAIN_ONSET, WARNING_ISSUED를 주면 두 룰 모두 실행된다")
    void enabled_both_rules() {
        // given
        Instant t1 = Instant.parse("2025-11-04T05:00:00Z");
        Instant t2 = Instant.parse("2025-11-04T06:00:00Z");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(1L, t1, 5, 70)
        ));
        when(warnRule.evaluate(anyList(), any())).thenReturn(List.of(
                warningEvent(2L, t2, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);
        List<String> out = service.generate(List.of(1L, 2L), enabled, t1, Locale.KOREA);

        // then
        assertThat(out).hasSize(2);
        assertThat(String.join("\n", out)).contains("비 시작").contains("발효");
        verify(rainRule, times(1)).evaluate(anyList(), any());
        verify(warnRule, times(1)).evaluate(anyList(), any());
    }

    @Test
    @DisplayName("receiveWarnings = true이면 WARNING_ISSUED 룰도 함께 실행된다")
    void generate_with_receiveWarnings_true_runs_warning_rule() {
        // given
        Instant t = Instant.parse("2025-11-04T05:00:00Z");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(1L, t, 5, 70)
        ));
        when(warnRule.evaluate(anyList(), any())).thenReturn(List.of(
                warningEvent(1L, t, WarningKind.RAIN, WarningLevel.ADVISORY)
        ));

        // when (since=null로 호출 → 내부에서 90분 윈도우를 적용)
        List<String> out = service.generate(List.of(1L), true, null);

        // then
        assertThat(out).hasSize(2);
        assertThat(String.join("\n", out)).contains("비 시작").contains("발효");
        verify(rainRule, times(1)).evaluate(anyList(), any());
        verify(warnRule, times(1)).evaluate(anyList(), any());
    }

    @Test
    @DisplayName("deduplicate: 동일 (type|region|occurredAt) 이벤트는 한 번만 출력된다")
    void deduplicate_removes_duplicates() {
        // given
        Instant t = Instant.parse("2025-11-04T05:00:00Z");
        AlertEvent dup1 = rainEvent(1L, t, 5, 70);
        AlertEvent dup2 = rainEvent(1L, t, 5, 70); // 같은 키(type|region|t)

        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(dup1, dup2));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        List<String> out = service.generate(List.of(1L), enabled, t, Locale.KOREA);

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).contains("비 시작");
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_is_capped_to_three() {
        // given: rainRule만 활성화, evaluate는 빈 목록 반환
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of());
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);

        // when
        List<Long> input = List.of(10L, 11L, 12L, 13L); // 4개 입력
        service.generate(input, enabled, Instant.parse("2025-11-04T05:00:00Z"), Locale.KOREA);

        // then: 전달된 regionIds는 앞 3개만
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(rainRule, times(1)).evaluate(captor.capture(), any());
        List<Long> passed = captor.getValue();
        assertThat(passed).containsExactly(10L, 11L, 12L);
    }

    @Test
    @DisplayName("정렬: 지역 → 타입명 → 발생시각 순으로 정렬된다")
    void sort_by_region_type_then_time() {
        // given
        Instant t1 = Instant.parse("2025-11-04T05:00:00Z");
        Instant t2 = Instant.parse("2025-11-04T06:00:00Z");

        // region 2에 비 시작, region 1에 특보, region 1에 비 시작(시각 t2)
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of(
                rainEvent(2L, t1, 5, 70),
                rainEvent(1L, t2, 6, 80)
        ));
        when(warnRule.evaluate(anyList(), any())).thenReturn(List.of(
                warningEvent(1L, t1, WarningKind.RAIN, WarningLevel.WARNING)
        ));

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED);

        // when
        List<String> out = service.generate(List.of(1L, 2L), enabled, Instant.parse("2025-11-04T04:00:00Z"), Locale.KOREA);

        // then: 정렬 규칙 = 룰 식별자(_srcRule) asc, region asc, type name asc, time asc
        //  - "RainOnsetChangeRule" < "WarningIssuedRule" 이므로 비 시작 2건이 먼저 묶임
        // 정렬 결과 예상:
        //  1) 지역 1 | 2025-11-04 15:00 | 6시 비 시작 (POP 80%)
        //  2) 지역 2 | 2025-11-04 14:00 | 5시 비 시작 (POP 70%)
        //  3) 지역 1 | 2025-11-04 14:00 | 호우 경보 발효
        //
        // 단, 문자열에는 타입명이 직접 들어가지 않으므로 "비 시작"/"발효" 키워드와 지역 번호로 순서를 대략 검증
        assertThat(out).hasSize(3);
        // 첫 줄: region 1 + 비 시작
        assertThat(out.get(0)).contains("지역 1").contains("비 시작");
        // 둘째 줄: region 2 + 비 시작
        assertThat(out.get(1)).contains("지역 2").contains("비 시작");
        // 셋째 줄: region 1 + 발효
        assertThat(out.get(2)).contains("지역 1").contains("발효");

//        System.out.println("=== 실제 생성된 문자열 출력 ===");
//        out.forEach(System.out::println);
    }

    @Test
    @DisplayName("since 값이 룰 evaluate 로 그대로 전달된다")
    void since_is_forwarded_to_rules() {
        Instant since = Instant.parse("2025-11-04T05:55:00Z");
        when(rainRule.evaluate(anyList(), any())).thenReturn(List.of());
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);

        // when
        service.generate(List.of(1L), enabled, since, Locale.KOREA);

        // then
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(rainRule).evaluate(anyList(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(since);
    }

    @Test
    @DisplayName("RAIN_FORECAST 포맷: hourlyParts/dayParts를 합쳐 문장을 만든다")
    void forecast_formatting_from_parts() {
        AlertRule forecastRule = mock(AlertRule.class);
        when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        Map<String, Object> payload = new HashMap<>();
        payload.put("hourlyParts", List.of("오늘 09~12시"));
        payload.put("dayParts", List.of("내일 오전"));

        when(forecastRule.evaluate(anyList(), any())).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 1L, Instant.parse("2025-11-04T05:00:00Z"), payload)
        ));

        NotificationGeneratorService svc = new NotificationGeneratorService(List.of(rainRule, warnRule, forecastRule));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        List<String> out = svc.generate(List.of(1L), enabled, Instant.parse("2025-11-04T04:00:00Z"), Locale.KOREA);

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).isEqualTo("지역 1 | 오늘 09~12시, 내일 오전 비 예보");
    }

    @Test
    @DisplayName("RAIN_FORECAST 포맷: 지역 3개 입력 시 각 지역별 한 줄씩 생성된다")
    void forecast_formatting_three_regions() {
        // given
        AlertRule forecastRule = mock(AlertRule.class);
        when(forecastRule.supports()).thenReturn(AlertTypeEnum.RAIN_FORECAST);

        Instant t = Instant.parse("2025-11-04T05:00:00Z");

        Map<String, Object> p1 = new HashMap<>();
        p1.put("hourlyParts", List.of("오늘 09~12시"));
        p1.put("dayParts", List.of("내일 오전"));

        Map<String, Object> p2 = new HashMap<>();
        p2.put("hourlyParts", List.of("오늘 15시"));
        p2.put("dayParts", List.of()); // 비어있어도 허용

        Map<String, Object> p3 = new HashMap<>();
        p3.put("hourlyParts", List.of()); // 비어있어도 허용
        p3.put("dayParts", List.of("모레 오후"));

        when(forecastRule.evaluate(anyList(), any())).thenReturn(List.of(
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 1L, t, p1),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 2L, t, p2),
                new AlertEvent(AlertTypeEnum.RAIN_FORECAST, 3L, t, p3)
        ));

        // SUT: forecastRule만 활성화
        NotificationGeneratorService svc = new NotificationGeneratorService(List.of(forecastRule));

        // when
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        List<String> out = svc.generate(List.of(1L, 2L, 3L), enabled, Instant.parse("2025-11-04T04:00:00Z"), Locale.KOREA);

        // then: 3개 지역 각각 한 줄
        assertThat(out).hasSize(3);
        assertThat(out.get(0)).isEqualTo("지역 1 | 오늘 09~12시, 내일 오전 비 예보");
        assertThat(out.get(1)).isEqualTo("지역 2 | 오늘 15시 비 예보");
        assertThat(out.get(2)).isEqualTo("지역 3 | 모레 오후 비 예보");

        // 전달된 regionIds가 정확히 [1,2,3]인지 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(forecastRule, times(1)).evaluate(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(1L, 2L, 3L);

//        System.out.println("=== 실제 생성된 문자열 출력 ===");
//        out.forEach(System.out::println);
    }

    // ---- helpers ----
    private static AlertEvent rainEvent(long regionId, Instant t, int hour, int pop) {
        return new AlertEvent(
                AlertTypeEnum.RAIN_ONSET,
                regionId,
                t,
                Map.of("hour", hour, "pop", pop)
        );
    }

    private static AlertEvent warningEvent(long regionId, Instant t, WarningKind kind, WarningLevel level) {
        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                t,
                Map.of("kind", kind, "level", level)
        );
    }
}
package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.rule.RainForecastRule;
import com.github.yun531.climate.service.notification.rule.RainOnsetChangeRule;
import com.github.yun531.climate.service.notification.rule.WarningIssuedRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 실제 스프링 빈(룰/리포지토리/서비스) + DB를 쓰는 통합 테스트.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        // --- 초기화
        "SET FOREIGN_KEY_CHECKS = 0",
        "DELETE FROM climate_snap",
        "DELETE FROM warning_state",
        "SET FOREIGN_KEY_CHECKS = 1",

        // --- climate_snap 시드
        "INSERT INTO climate_snap VALUES " +
                "(10, 1,  '2025-11-18 08:00:00'," +
                " 1,2,3,4,5,6,7,8,9,10,11,12,13,12,11,10,9,8,7,6,5,4,3,2,1,5," +
                "  5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6," +
                "  50,40,50,60,60, 60,40,30,20,10, 0,0,10,20,20, 30,40,60,10,0, 20,40,70,40,60,70," +
                "  70,40, 40,40, 30,30, 40,40, 30,60, 50,50, 40,40)," +
                "(1, 1, '2025-11-18 11:00:00'," +
                "  1,2,3,4,5,6,7,8,9,10,11,12,13,12,11,10,9,8,7,6,5,4,3,2,1,5," +
                "  5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6," +
                "  40,50,60,60,60, 60,30,20,10,0, 0,10,20,20,30, 60,60,60,0,20, 40,70,40,60,70,80," +
                "  30,30, 40,40, 30,60, 50,50, 40,40, 20,20, 0,0)",

        // --- warning_state 시드
        "INSERT INTO warning_state (region_id, kind, level, updated_at) VALUES " +
                "(1, 'RAIN',  'ADVISORY', '2025-11-04 05:00:00')," +
                "(1, 'HEAT',  'WARNING',  '2025-11-04 06:30:00')," +
                "(2, 'WIND',  'ADVISORY', '2025-11-04 07:15:00')"
})
@Import(NotificationServiceIT.SpyConfig.class)
class NotificationServiceIT {

    @Autowired
    private NotificationService service;

    @Autowired
    private RainOnsetChangeRule rainRule;      // SpyConfig 에서 주입되는 spy

    @Autowired
    private WarningIssuedRule warningRule;     // SpyConfig 에서 주입되는 spy

    @Autowired
    private RainForecastRule forecastRule;     // SpyConfig 에서 주입되는 spy

    @TestConfiguration
    static class SpyConfig {

        @Bean(name = "rainOnsetChangeRule")
        RainOnsetChangeRule rainOnsetChangeRuleSpy(SnapshotQueryService snapshotQueryService) {
            return Mockito.spy(new RainOnsetChangeRule(snapshotQueryService));
        }

        @Bean(name = "warningIssuedRule")
        WarningIssuedRule warningIssuedRuleSpy(WarningStateQueryService warningStateQueryService) {
            return Mockito.spy(new WarningIssuedRule(warningStateQueryService));
        }

        @Bean(name = "rainForecastRule")
        RainForecastRule rainForecastRuleSpy(SnapshotQueryService snapshotQueryService) {
            return Mockito.spy(new RainForecastRule(snapshotQueryService));
        }
    }

    /**
     *   기본 동작 / 공통 규칙
     */

    @Test
    @DisplayName("receiveWarnings=false: 비 시작(AlertType=RAIN_ONSET) 알림만 생성되고 특보(WARNING_ISSUED)는 제외된다")
    void only_rain_when_receiveWarnings_false() {
        int regionId01 = 1;
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        NotificationRequest request = new NotificationRequest(
                List.of(regionId01),
                since,
                null,    // enabledTypes -> null이면 normalizeEnabledTypes 에서 기본값 적용(RAIN_ONSET)
                null,    // filterWarningKinds
                null     // rainHourLimit
        );

        // when
        List<AlertEvent> events = service.generate(request);

        // then
        assertThat(events).isNotEmpty();
        assertThat(events)
                .extracting(AlertEvent::type)
                .contains(AlertTypeEnum.RAIN_ONSET)
                .doesNotContain(AlertTypeEnum.WARNING_ISSUED);

        verify(rainRule, times(1)).evaluate(any(NotificationRequest.class));
        verify(warningRule, never()).evaluate(any(NotificationRequest.class));
        // FORECAST는 enabledTypes에 없으므로 호출 안 됨
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_capped_to_three() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        var regionIds = List.of(10, 11, 12, 13); // 4개 입력
        Set<AlertTypeEnum> enabled =
                EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED, AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        // when
        service.generate(request);

        // then: RainOnsetChangeRule 쪽 전달 인자 캡처
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());
        var passed = captor.getValue().regionIds();

        assertThat(passed).containsExactly(10, 11, 12);
    }

    @Test
    @DisplayName("정렬: 타입 → 지역 → 타입명 → 시각 순으로 정렬된다")
    void sort_rule_applied() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        var regionIds = List.of(1, 2);
        Set<AlertTypeEnum> enabled =
                EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED, AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        // when
        List<AlertEvent> events = service.generate(request);

        assertThat(events).isNotEmpty();

        // 1) 같은 리스트 내에서 RAIN_ONSET 들이 WARNING_ISSUED 들보다 앞에 오는지
        List<Integer> rainIdx = new ArrayList<>();
        List<Integer> warnIdx = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            AlertEvent e = events.get(i);
            if (e.type() == AlertTypeEnum.RAIN_ONSET) {
                rainIdx.add(i);
            }
            if (e.type() == AlertTypeEnum.WARNING_ISSUED) {
                warnIdx.add(i);
            }
        }
        assertThat(rainIdx).isNotEmpty();
        assertThat(warnIdx).isNotEmpty();

        int lastRainIndex = Collections.max(rainIdx);
        int firstWarnIndex = Collections.min(warnIdx);

        assertThat(lastRainIndex).isLessThan(firstWarnIndex);

        // 2) 같은 타입 블록 내에서는 regionId 오름차순인지 대략 확인
        List<AlertEvent> rainEvents = events.stream()
                .filter(e -> e.type() == AlertTypeEnum.RAIN_ONSET)
                .toList();

        List<Integer> regionNums = rainEvents.stream()
                .map(AlertEvent::regionId)
                .toList();

        assertThat(regionNums).isSorted(); // region asc
    }

    /**
     *  필터 분기: WarningIssuedRule / RainOnsetChangeRule 에 전달되는 NotificationRequest
     */

    @Test
    @DisplayName("filterWarningKinds가 있으면 해당 값이 포함된 NotificationRequest가 WARNING_ISSUED 룰로 전달된다")
    void filterWarningKinds_is_forwarded_in_request() {
        var regionIds = List.of(1, 2);
        LocalDateTime since = LocalDateTime.parse("2025-11-04T04:00:00");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.WARNING_ISSUED);
        Set<WarningKind> kinds = EnumSet.of(WarningKind.RAIN);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                kinds,      // filterKinds
                null        // rainHourLimit
        );

        // when
        List<AlertEvent> events = service.generate(request);

        // WARNING_ISSUED만 포함되는지, 그리고 kind=RAIN만 있는지 확인
        assertThat(events)
                .isNotEmpty()
                .allMatch(e -> e.type() == AlertTypeEnum.WARNING_ISSUED);
        assertThat(events)
                .extracting(e -> e.payload().get("kind"))
                .containsOnly(WarningKind.RAIN);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(warningRule, times(1))
                .evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly(1, 2);
        assertThat(passed.filterWarningKinds()).containsExactly(WarningKind.RAIN);
        assertThat(passed.since()).isEqualTo(since);

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("rainHourLimit가 있으면 해당 값과 함께 NotificationRequest가 RAIN_ONSET 룰로 전달된다")
    void rainHourLimit_is_forwarded_in_request() {
        var regionIds = List.of(10, 11, 12, 13); // 4개 → limitRegions 로 앞 3개만 사용
        LocalDateTime since = LocalDateTime.parse("2025-11-04T04:00:00");
        int limitHour = 12;

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                limitHour
        );

        // when
        List<AlertEvent> events = service.generate(request);

        // then: RainOnsetChangeRule에 전달된 NotificationRequest 내용 검증
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1))
                .evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        // limitRegions 적용 확인 (앞 3개만 전달)
        assertThat(passed.regionIds()).containsExactly(10, 11, 12);
        assertThat(passed.since()).isEqualTo(since);
        assertThat(passed.rainHourLimit()).isEqualTo(limitHour);

        verify(warningRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    /**
     *  RAIN_FORECAST: RainForecastRule 통합 동작 검증
     */

    @Test
    @DisplayName("RAIN_FORECAST: enabledTypes에 RAIN_FORECAST만 있으면 예보 요약 이벤트만 생성되고 다른 룰은 호출되지 않는다")
    void only_forecast_when_enabled_rain_forecast() {
        var since = LocalDateTime.parse("2025-11-18T07:00:00");
        var regionIds = List.of(1);   // climate_snap 시드에 존재하는 지역

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        // when
        List<AlertEvent> events = service.generate(request);

        // then
        assertThat(events).isNotEmpty();
        assertThat(events)
                .extracting(AlertEvent::type)
                .containsOnly(AlertTypeEnum.RAIN_FORECAST);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(forecastRule, times(1)).evaluate(captor.capture());
        assertThat(captor.getValue().regionIds()).containsExactlyElementsOf(regionIds);

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(warningRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("RAIN_FORECAST: payload에 hourlyParts/dayParts가 포함되고 형식이 유지된다")
    void forecast_payload_structure_is_valid() {
        var since = LocalDateTime.parse("2025-11-18T07:00:00");
        var regionIds = List.of(1);

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        // when
        List<AlertEvent> events = service.generate(request);

        // then
        assertThat(events).hasSize(1);
        AlertEvent e = events.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_FORECAST);
        assertThat(e.regionId()).isEqualTo(1);

        Map<String, Object> payload = e.payload();
        assertThat(payload).containsKeys("_srcRule", "hourlyParts", "dayParts");

        @SuppressWarnings("unchecked")
        List<List<Integer>> hourly = (List<List<Integer>>) payload.get("hourlyParts");
        @SuppressWarnings("unchecked")
        List<List<Integer>> day = (List<List<Integer>>) payload.get("dayParts");

        // hourlyParts: [startHourOff, endHourOff] 쌍 리스트, hourOff는 1~24 범위, start <= end
        for (List<Integer> part : hourly) {
            assertThat(part).hasSize(2);
            assertThat(part.get(0)).isBetween(1, 24);
            assertThat(part.get(1)).isBetween(1, 24);
            assertThat(part.get(0)).isLessThanOrEqualTo(part.get(1));
        }

        // dayParts: [amFlag, pmFlag] 쌍 리스트, 값은 0 또는 1
        for (List<Integer> dp : day) {
            assertThat(dp).hasSize(2);
            assertThat(dp.get(0)).isIn(0, 1);
            assertThat(dp.get(1)).isIn(0, 1);
        }
    }
}
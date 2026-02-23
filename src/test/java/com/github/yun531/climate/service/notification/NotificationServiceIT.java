package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.payload.DailyRainFlags;
import com.github.yun531.climate.service.notification.model.payload.RainForecastPayload;
import com.github.yun531.climate.service.notification.model.payload.RainInterval;
import com.github.yun531.climate.service.notification.model.payload.WarningIssuedPayload;
import com.github.yun531.climate.service.notification.rule.RainForecastRule;
import com.github.yun531.climate.service.notification.rule.RainOnsetChangeRule;
import com.github.yun531.climate.service.notification.rule.WarningIssuedRule;
import com.github.yun531.climate.service.notification.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.service.notification.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.notification.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

        // --- nowHour / reportTime 세팅 (세션 변수)
        "SET @now_hr := STR_TO_DATE(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), '%Y-%m-%d %H:%i:%s')",
        "SET @rt_cur := @now_hr",
        "SET @rt_prev := DATE_SUB(@rt_cur, INTERVAL 3 HOUR)",

        // --- climate_snap 시드 (valid_at_* 제거, series_start_time 추가)
        "INSERT INTO climate_snap ("
                + " snap_id, region_id, report_time, series_start_time,"

                + " temp_a01,temp_a02,temp_a03,temp_a04,temp_a05,temp_a06,temp_a07,temp_a08,temp_a09,"
                + " temp_a10,temp_a11,temp_a12,temp_a13,temp_a14,temp_a15,temp_a16,temp_a17,temp_a18,temp_a19,"
                + " temp_a20,temp_a21,temp_a22,temp_a23,temp_a24,temp_a25,temp_a26,"

                + " temp_a0d_max,temp_a0d_min,temp_a1d_max,temp_a1d_min,temp_a2d_max,temp_a2d_min,"
                + " temp_a3d_max,temp_a3d_min,temp_a4d_max,temp_a4d_min,temp_a5d_max,temp_a5d_min,temp_a6d_max,temp_a6d_min,"

                + " pop_a01,pop_a02,pop_a03,pop_a04,pop_a05,pop_a06,pop_a07,pop_a08,pop_a09,"
                + " pop_a10,pop_a11,pop_a12,pop_a13,pop_a14,pop_a15,pop_a16,pop_a17,pop_a18,pop_a19,"
                + " pop_a20,pop_a21,pop_a22,pop_a23,pop_a24,pop_a25,pop_a26,"

                + " pop_a0d_am,pop_a0d_pm,pop_a1d_am,pop_a1d_pm,pop_a2d_am,pop_a2d_pm,"
                + " pop_a3d_am,pop_a3d_pm,pop_a4d_am,pop_a4d_pm,pop_a5d_am,pop_a5d_pm,pop_a6d_am,pop_a6d_pm"
                + ") VALUES "

                // ---- prev (snap_id=10)
                + "(10, '1', @rt_prev, DATE_ADD(@rt_prev, INTERVAL 1 HOUR), "
                + " 1,2,3,4,5,6,7,8,9, 10,11,12,13,12,11,10,9,8,7, 6,5,4,3,2,1,5,"
                + " 5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6,"
                + " 50,40,50,60,60, 60,40,30,20, 10,0,0,10,20,20, 30,40,60,10, 0,20,40,70,40,60,70,"
                + " 70,40, 40,40, 30,30, 40,40, 30,60, 50,50, 40,40"
                + "),"

                // ---- cur (snap_id=1)
                + "(1, '1', @rt_cur, DATE_ADD(@rt_cur, INTERVAL 1 HOUR), "
                + " 1,2,3,4,5,6,7,8,9, 10,11,12,13,12,11,10,9,8,7, 6,5,4,3,2,1,5,"
                + " 5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6,"
                + " 40,50,60,60,60, 60,30,20,10, 0,0,10,20,20,30, 60,60,60, 0,20,40,70,40,60,70,80,"
                + " 30,30, 40,40, 30,60, 50,50, 40,40, 20,20, 0,0"
                + ")",

        // --- warning_state 시드
        "INSERT INTO warning_state (region_id, kind, level, updated_at) VALUES "
                + "('1', 'RAIN',  'ADVISORY', '2025-11-04 05:00:00'),"
                + "('1', 'HEAT',  'WARNING',  '2025-11-04 06:30:00'),"
                + "('2', 'WIND',  'ADVISORY', '2025-11-04 07:15:00')"
})
@Import(NotificationServiceIT.SpyConfig.class)
class NotificationServiceIT {

    @Autowired
    private NotificationService service;

    @Autowired
    private RainOnsetChangeRule rainRule;

    @Autowired
    private WarningIssuedRule warningRule;

    @Autowired
    private RainForecastRule forecastRule;

    @TestConfiguration
    static class SpyConfig {

        @Bean(name = "rainOnsetChangeRule")
        RainOnsetChangeRule rainOnsetChangeRuleSpy(
                SnapshotQueryService snapshotQueryService,
                RainOnsetEventValidAtAdjuster adjuster,
                RainOnsetEventComputer computer,
                @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes
        ) {
            return Mockito.spy(new RainOnsetChangeRule(
                    snapshotQueryService,
                    adjuster,
                    computer,
                    recomputeThresholdMinutes
            ));
        }

        @Bean(name = "warningIssuedRule")
        WarningIssuedRule warningIssuedRuleSpy(
                WarningStateQueryService warningStateQueryService,
                @Value("${notification.warning.cache-ttl-minutes:45}") int ttlMinutes,
                @Value("${notification.warning.since-adjust-minutes:90}") int sinceAdjustMinutes
        ) {
            return Mockito.spy(new WarningIssuedRule(warningStateQueryService, ttlMinutes, sinceAdjustMinutes));
        }

        @Bean(name = "rainForecastRule")
        RainForecastRule rainForecastRuleSpy(
                SnapshotQueryService snapshotQueryService,
                RainForecastComputer computer,
                RainForecastPartsAdjuster adjuster,
                @Value("${notification.recompute-threshold-minutes:165}") int recomputeThresholdMinutes,
                @Value("${notification.threshold-pop:60}") int thresholdPop
        ) {
            return Mockito.spy(new RainForecastRule(
                    snapshotQueryService,
                    computer,
                    adjuster,
                    recomputeThresholdMinutes,
                    thresholdPop
            ));
        }
    }

    /* =======================
     * 기본 동작 / 공통 규칙
     * ======================= */

    @Test
    @DisplayName("receiveWarnings=false: 비 시작(RAIN_ONSET)만 생성되고 WARNING_ISSUED는 제외된다")
    void only_rain_when_receiveWarnings_false() {
        String regionId01 = "1";
        var since = LocalDateTime.parse("2025-11-04T04:00:00");

        NotificationRequest request = new NotificationRequest(
                List.of(regionId01),
                since,
                EnumSet.of(AlertTypeEnum.RAIN_ONSET),
                null,
                null
        );

        List<AlertEvent> events = service.generate(request);

        assertThat(events).isNotEmpty();
        assertThat(events)
                .extracting(AlertEvent::type)
                .contains(AlertTypeEnum.RAIN_ONSET)
                .doesNotContain(AlertTypeEnum.WARNING_ISSUED);

        verify(rainRule, times(1)).evaluate(any(NotificationRequest.class));
        verify(warningRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("enabledTypes=null: 아무 룰도 실행되지 않고 빈 리스트를 반환한다")
    void none_when_enabledTypes_null() {
        String regionId01 = "1";
        var since = LocalDateTime.parse("2025-11-04T04:00:00");

        NotificationRequest request = new NotificationRequest(
                List.of(regionId01),
                since,
                null,
                null,
                null
        );

        List<AlertEvent> events = service.generate(request);

        assertThat(events).isEmpty();
        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(warningRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_capped_to_three() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        var regionIds = List.of("10", "11", "12", "13");
        Set<AlertTypeEnum> enabled =
                EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED, AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        service.generate(request);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());

        var passed = captor.getValue().regionIds();
        assertThat(passed).containsExactly("10", "11", "12");
    }

    @Test
    @DisplayName("정렬: 타입 → 지역 → 시각 순으로 정렬된다")
    void sort_rule_applied() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        var regionIds = List.of("1", "2");
        Set<AlertTypeEnum> enabled =
                EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED, AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        List<AlertEvent> events = service.generate(request);

        assertThat(events).isNotEmpty();

        List<Integer> rainIdx = new ArrayList<>();
        List<Integer> warnIdx = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            AlertEvent e = events.get(i);
            if (e.type() == AlertTypeEnum.RAIN_ONSET) rainIdx.add(i);
            if (e.type() == AlertTypeEnum.WARNING_ISSUED) warnIdx.add(i);
        }
        assertThat(rainIdx).isNotEmpty();
        assertThat(warnIdx).isNotEmpty();

        int lastRainIndex = Collections.max(rainIdx);
        int firstWarnIndex = Collections.min(warnIdx);
        assertThat(lastRainIndex).isLessThan(firstWarnIndex);

        List<AlertEvent> rainEvents = events.stream()
                .filter(e -> e.type() == AlertTypeEnum.RAIN_ONSET)
                .toList();

        List<String> regionNums = rainEvents.stream()
                .map(AlertEvent::regionId)
                .toList();

        assertThat(regionNums).isSorted();
    }

    /* =======================
     * 필터/파라미터 전달 검증
     * ======================= */

    @Test
    @DisplayName("filterWarningKinds가 있으면 해당 값이 WARNING_ISSUED 룰로 전달된다")
    void filterWarningKinds_is_forwarded_in_request() {
        var regionIds = List.of("1", "2");
        LocalDateTime since = LocalDateTime.parse("2025-11-04T04:00:00");
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.WARNING_ISSUED);
        Set<WarningKind> kinds = EnumSet.of(WarningKind.RAIN);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                kinds,
                null
        );

        List<AlertEvent> events = service.generate(request);

        assertThat(events)
                .isNotEmpty()
                .allMatch(e -> e.type() == AlertTypeEnum.WARNING_ISSUED);

        assertThat(events)
                .extracting(AlertEvent::payload)
                .allMatch(p -> p instanceof WarningIssuedPayload);

        assertThat(events)
                .extracting(e -> ((WarningIssuedPayload) e.payload()).kind())
                .containsOnly(WarningKind.RAIN);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(warningRule, times(1)).evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly("1", "2");
        assertThat(passed.filterWarningKinds()).containsExactly(WarningKind.RAIN);
        assertThat(passed.since()).isEqualTo(since);

        verify(rainRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    @Test
    @DisplayName("rainHourLimit가 있으면 해당 값과 함께 RAIN_ONSET 룰로 전달된다")
    void rainHourLimit_is_forwarded_in_request() {
        var regionIds = List.of("10", "11", "12", "13");
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

        service.generate(request);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(rainRule, times(1)).evaluate(captor.capture());

        NotificationRequest passed = captor.getValue();
        assertThat(passed.regionIds()).containsExactly("10", "11", "12");
        assertThat(passed.since()).isEqualTo(since);
        assertThat(passed.rainHourLimit()).isEqualTo(limitHour);

        verify(warningRule, never()).evaluate(any(NotificationRequest.class));
        verify(forecastRule, never()).evaluate(any(NotificationRequest.class));
    }

    /* =======================
     * RAIN_FORECAST 검증
     * ======================= */

    @Test
    @DisplayName("RAIN_FORECAST: enabledTypes에 RAIN_FORECAST만 있으면 예보 요약 이벤트만 생성된다")
    void only_forecast_when_enabled_rain_forecast() {
        var since = LocalDateTime.parse("2025-11-18T07:00:00");
        var regionIds = List.of("1");

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        List<AlertEvent> events = service.generate(request);

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
        var regionIds = List.of("1");

        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                enabled,
                null,
                null
        );

        List<AlertEvent> events = service.generate(request);

        assertThat(events).hasSize(1);

        AlertEvent e = events.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_FORECAST);
        assertThat(e.regionId()).isEqualTo("1");

        assertThat(e.payload()).isInstanceOf(RainForecastPayload.class);
        RainForecastPayload payload = (RainForecastPayload) e.payload();

        assertThat(payload.hourlyParts()).isNotNull();
        for (RainInterval part : payload.hourlyParts()) {
            assertThat(part).isNotNull();
            assertThat(part.start()).isNotNull();
            assertThat(part.end()).isNotNull();
            assertThat(part.start()).isBeforeOrEqualTo(part.end());
        }

        assertThat(payload.dayParts()).isNotNull();
        assertThat(payload.dayParts()).hasSize(7);

        for (DailyRainFlags flags : payload.dayParts()) {
            assertThat(flags).isNotNull();
            boolean am = flags.rainAm();
            boolean pm = flags.rainPm();
        }
    }

    @Test
    @DisplayName("RAIN_FORECAST: hourlyParts는 시간 역행 없이 (start 기준) 오름차순으로 유지된다")
    void forecast_hourly_parts_are_monotonic() {
        var since = LocalDateTime.parse("2025-11-18T07:00:00");
        var regionIds = List.of("1");

        NotificationRequest request = new NotificationRequest(
                regionIds,
                since,
                EnumSet.of(AlertTypeEnum.RAIN_FORECAST),
                null,
                null
        );

        List<AlertEvent> events = service.generate(request);
        assertThat(events).hasSize(1);

        RainForecastPayload payload = (RainForecastPayload) events.get(0).payload();
        List<RainInterval> parts = payload.hourlyParts();

        // empty면 규칙이 "비 없음"으로 줄 수도 있으니, monotonic 체크는 있을 때만 강하게
        if (parts != null && parts.size() >= 2) {
            for (int i = 1; i < parts.size(); i++) {
                RainInterval prev = parts.get(i - 1);
                RainInterval cur = parts.get(i);

                // start 기준 오름차순
                assertThat(cur.start()).isAfterOrEqualTo(prev.start());

                // 구간이 겹치지 않도록(정책에 따라 같을 수도 있으니 OrEqual)
                assertThat(cur.start()).isAfterOrEqualTo(prev.end());
            }
        }
    }
}
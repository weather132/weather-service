package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainForecastDetectorTest {

    private static final int THRESHOLD  = 60;
    private static final int MAX_POINTS = 26;
    private final RainForecastDetector detector = new RainForecastDetector(THRESHOLD, MAX_POINTS);

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDateTime NOW           = LocalDateTime.of(2026, 1, 22, 5, 15);

    //  --- null / blank 가드 ---

    @Nested
    @DisplayName("null/blank 가드")
    class NullBlankGuards {

        @Test
        @DisplayName("null 입력 -> null 반환")
        void nullInputs_returnsNull() {
            assertThat(detector.detect(null, buildPopView(ANNOUNCE_TIME, new Integer[26]), NOW)).isNull();
            assertThat(detector.detect("R1", null, NOW)).isNull();
            assertThat(detector.detect("R1", buildPopView(ANNOUNCE_TIME, new Integer[26]), null)).isNull();
        }

        @Test
        @DisplayName("blank regionId -> null 반환")
        void blankRegionId_returnsNull() {
            PopView view = buildPopView(ANNOUNCE_TIME, new Integer[26]);
            assertThat(detector.detect("", view, NOW)).isNull();
            assertThat(detector.detect("   ", view, NOW)).isNull();
        }
    }

    //  --- hourly 비 구간 탐지 ---

    @Nested
    @DisplayName("hourly 비 구간 탐지")
    class HourlyRainDetection {

        @Test
        @DisplayName("비 없음 -> hourlyParts 비어 있음")
        void noRain_emptyHourlyParts() {
            Integer[] pops = new Integer[26];
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).isEmpty();
        }

        @Test
        @DisplayName("연속 비 구간 -> hourlyParts에 RainInterval 1개 생성")
        void consecutiveRain_producesInterval() {
            Integer[] pops = {0, 0, 0, THRESHOLD, THRESHOLD+20, THRESHOLD+30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = detector.detect("R1", view, NOW);

            assertThat(event).isNotNull();
            assertThat(event.type()).isEqualTo(AlertTypeEnum.RAIN_FORECAST);

            RainForecastPayload payload = (RainForecastPayload) event.payload();
            assertThat(payload.hourlyParts()).hasSize(1);
            assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(4));
            assertThat(payload.hourlyParts().get(0).end()).isEqualTo(ANNOUNCE_TIME.plusHours(6));
        }

        @Test
        @DisplayName("끊어진 비 구간 -> hourlyParts에 RainInterval 2개 생성")
        void multipleRainSegments() {
            Integer[] pops = {THRESHOLD, THRESHOLD+10, 0, 0, THRESHOLD+30, THRESHOLD+10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).hasSize(2);
        }

        @Test
        @DisplayName("null POP -> 비 구간 끊김 처리")
        void nullPop_breaksRainSegment() {
            Integer[] pops = new Integer[26];
            pops[0] = THRESHOLD+20;
            pops[1] = null;
            pops[2] = THRESHOLD+10;
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).hasSize(2);
        }

        @Test
        @DisplayName("validAt이 null인 포인트 스킵 후 루프 지속")
        void validAtNull_skipsAndContinues() {
            List<Hourly.Pop> hourlyPops = new ArrayList<>();
            hourlyPops.add(new Hourly.Pop(ANNOUNCE_TIME.plusHours(1), THRESHOLD+10));
            hourlyPops.add(new Hourly.Pop(null, THRESHOLD+20));
            hourlyPops.add(new Hourly.Pop(ANNOUNCE_TIME.plusHours(3), THRESHOLD+10));

            for (int i = 3; i < 26; i++) {
                hourlyPops.add(new Hourly.Pop(ANNOUNCE_TIME.plusHours(i + 1), null));
            }

            List<Daily.Pop> dailyPops = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) dailyPops.add(new Daily.Pop(null, null));
            PopView view = new PopView(new Hourly(hourlyPops), new Daily(dailyPops), ANNOUNCE_TIME);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).hasSize(2);
            assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
            assertThat(payload.hourlyParts().get(0).end()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
            assertThat(payload.hourlyParts().get(1).start()).isEqualTo(ANNOUNCE_TIME.plusHours(3));
            assertThat(payload.hourlyParts().get(1).end()).isEqualTo(ANNOUNCE_TIME.plusHours(3));
        }

        @Nested
        @DisplayName("hourly 경계값")
        class HourlyBoundaryValues {

            @Test
            @DisplayName("pop = threshold(60) -> 비로 판정")
            void exactlyThreshold_isRainy() {
                Integer[] pops = new Integer[26];
                pops[0] = THRESHOLD;
                pops[1] = THRESHOLD;
                PopView view = buildPopView(ANNOUNCE_TIME, pops);

                AlertEvent event = detector.detect("R1", view, NOW);
                assertThat(event).isNotNull();
                RainForecastPayload payload = (RainForecastPayload) event.payload();

                assertThat(payload.hourlyParts()).hasSize(1);
                assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
                assertThat(payload.hourlyParts().get(0).end()).isEqualTo(ANNOUNCE_TIME.plusHours(2));
            }

            @Test
            @DisplayName("pop = 59 (threshold - 1) -> 비 아님")
            void justBelowThreshold_notRainy() {
                Integer[] pops = new Integer[26];
                pops[0] = THRESHOLD-1;
                PopView view = buildPopView(ANNOUNCE_TIME, pops);

                AlertEvent event = detector.detect("R1", view, NOW);
                assertThat(event).isNotNull();
                RainForecastPayload payload = (RainForecastPayload) event.payload();

                assertThat(payload.hourlyParts()).isEmpty();
            }
        }
    }

    //  --- 구간 닫기 / 최대 포인트 절단 ---

    @Nested
    @DisplayName("구간 닫기 / 최대 포인트 절단")
    class SegmentClosing {

        @Test
        @DisplayName("마지막 포인트까지 비 -> 열린 구간 자동 닫기")
        void lastPointRainy_closesOpenSegment() {
            Integer[] pops = new Integer[26];
            pops[24] = THRESHOLD+20;
            pops[25] = THRESHOLD+10;
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).hasSize(1);
            assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(25));
            assertThat(payload.hourlyParts().get(0).end()).isEqualTo(ANNOUNCE_TIME.plusHours(26));
        }

        @Test
        @DisplayName("전체 포인트가 비 -> 단일 구간으로 묶임")
        void allPointsRainy_singleSegment() {
            Integer[] pops = new Integer[26];
            for (int i = 0; i < 26; i++) pops[i] = THRESHOLD+10;
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).hasSize(1);
            assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
            assertThat(payload.hourlyParts().get(0).end()).isEqualTo(ANNOUNCE_TIME.plusHours(26));
        }

        @Test
        @DisplayName("maxHourlyPoints 초과 포인트 절단 -> 이후 포인트 무시")
        void maxHourlyPoints_truncates() {
            RainForecastDetector smallDetector = new RainForecastDetector(THRESHOLD, 5);

            Integer[] pops = new Integer[26];
            pops[0] = THRESHOLD+10; pops[1] = THRESHOLD+20; pops[2] = THRESHOLD+30;
            pops[3] = 0;
            pops[4] = THRESHOLD+10; pops[5] = THRESHOLD+20; pops[6] = THRESHOLD+30;
            PopView view = buildPopView(ANNOUNCE_TIME, pops);

            AlertEvent event = smallDetector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.hourlyParts()).hasSize(2);
            assertThat(payload.hourlyParts().get(1).start())
                    .isEqualTo(payload.hourlyParts().get(1).end());
        }
    }

    //  --- daily 비 판정 ---

    @Nested
    @DisplayName("daily 비 판정")
    class DailyRainDetection {

        @Test
        @DisplayName("dailyPop AM >= threshold, PM < threshold -> rainAm=true, rainPm=false")
        void dailyFlags_amPmMapping() {
            Integer[] pops = new Integer[26];
            PopView view = buildPopViewWithDaily(ANNOUNCE_TIME, pops, THRESHOLD+10, THRESHOLD-30);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.dayParts()).isNotEmpty();
            assertThat(payload.dayParts().get(0).rainAm()).isTrue();
            assertThat(payload.dayParts().get(0).rainPm()).isFalse();
        }

        @Test
        @DisplayName("dailyPop AM/PM 모두 null -> rainAm=false, rainPm=false")
        void nullDailyPop_allFalse() {
            Integer[] pops = new Integer[26];
            PopView view = buildPopViewWithDaily(ANNOUNCE_TIME, pops, null, null);

            AlertEvent event = detector.detect("R1", view, NOW);
            assertThat(event).isNotNull();
            RainForecastPayload payload = (RainForecastPayload) event.payload();

            assertThat(payload.dayParts().get(0).rainAm()).isFalse();
            assertThat(payload.dayParts().get(0).rainPm()).isFalse();
        }

        @Nested
        @DisplayName("daily 경계값")
        class DailyBoundaryValues {

            @Test
            @DisplayName("dailyPop AM = threshold(60), PM = 59 -> rainAm=true, rainPm=false")
            void dailyAmExactThreshold_true() {
                Integer[] pops = new Integer[26];
                PopView view = buildPopViewWithDaily(ANNOUNCE_TIME, pops, THRESHOLD, THRESHOLD-1);

                AlertEvent event = detector.detect("R1", view, NOW);
                assertThat(event).isNotNull();
                RainForecastPayload payload = (RainForecastPayload) event.payload();

                assertThat(payload.dayParts().get(0).rainAm()).isTrue();
                assertThat(payload.dayParts().get(0).rainPm()).isFalse();
            }
        }
    }

    //  --- occurredAt 계산 ---

    @Nested
    @DisplayName("occurredAt 계산")
    class OccurredAt {

        @Test
        @DisplayName("announceTime 존재 -> truncateToMinutes(announceTime)이 occurredAt")
        void reportTimePresent_usedAsOccurredAt() {
            LocalDateTime reportTime = LocalDateTime.of(2026, 1, 22, 5, 30);
            Integer[] pops = new Integer[26];
            pops[0] = THRESHOLD+10;
            PopView view = buildPopViewWithReportTime(reportTime, pops);

            AlertEvent event = detector.detect("R1", view, NOW);

            assertThat(event).isNotNull();
            assertThat(event.occurredAt()).isEqualTo(reportTime);
        }

        @Test
        @DisplayName("announceTime = null -> now가 occurredAt 으로 대체")
        void reportTimeNull_nowUsed() {
            Integer[] pops = new Integer[26];
            pops[0] = THRESHOLD;
            PopView view = buildPopViewWithReportTime(null, pops);

            AlertEvent event = detector.detect("R1", view, NOW);

            assertThat(event).isNotNull();
            assertThat(event.occurredAt()).isEqualTo(NOW);
        }
    }

    //  --- 헬퍼 ---

    private PopView buildPopView(LocalDateTime reportTime, Integer[] pops) {
        return buildPopViewWithDaily(reportTime, pops, null, null);
    }

    private PopView buildPopViewWithReportTime(LocalDateTime reportTime, Integer[] pops) {
        return buildPopViewWithDaily(reportTime, pops, null, null);
    }

    private PopView buildPopViewWithDaily(LocalDateTime reportTime, Integer[] pops,
                                          Integer day0AmPop, Integer day0PmPop) {
        LocalDateTime baseTime = reportTime != null ? reportTime : ANNOUNCE_TIME;
        List<Hourly.Pop> hourlyPops = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            Integer pop = (i < pops.length) ? pops[i] : null;
            hourlyPops.add(new Hourly.Pop(baseTime.plusHours(i + 1), pop));
        }

        List<Daily.Pop> dailyPops = new ArrayList<>(7);
        dailyPops.add(new Daily.Pop(day0AmPop, day0PmPop));
        for (int i = 1; i < 7; i++) dailyPops.add(new Daily.Pop(null, null));

        return new PopView(new Hourly(hourlyPops), new Daily(dailyPops), reportTime);
    }
}
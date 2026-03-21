package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainOnsetDetectorTest {

    private static final int THRESHOLD  = 60;
    private static final int MAX_POINTS = 26;

    private final RainOnsetDetector detector = new RainOnsetDetector(THRESHOLD, MAX_POINTS);

    private static final LocalDateTime ANNOUNCE_TIME     = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDateTime NOW               = LocalDateTime.of(2026, 1, 22, 5, 15);
    /** POP 시계열 시작 시각 (발표 +1h = 06:00~) */
    private static final LocalDateTime SERIES_START_TIME = ANNOUNCE_TIME.plusHours(1);

    //  --- null / blank 가드 ---

    @Nested
    @DisplayName("null/blank 가드")
    class NullGuards {

        @Test
        @DisplayName("pair가 null 이면 빈 리스트")
        void nullPair_empty() {
            assertThat(detector.detect("R1", null, NOW)).isEmpty();
        }

        @Test
        @DisplayName("regionId가 blank 이면 빈 리스트")
        void blankRegion_empty() {
            PopView view = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, toIntegers(THRESHOLD));
            assertThat(detector.detect("", new PopView.Pair(view, view), NOW)).isEmpty();
        }

        @Test
        @DisplayName("null regionId -> 빈 리스트")
        void nullRegionId_empty() {
            PopView view = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, toIntegers(THRESHOLD));
            assertThat(detector.detect(null, new PopView.Pair(view, view), NOW)).isEmpty();
        }

        @Test
        @DisplayName("null now -> 빈 리스트")
        void nullNow_empty() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD));
            assertThat(detector.detect("R1", new PopView.Pair(cur, prev), null)).isEmpty();
        }

        @Test
        @DisplayName("pair.current() == null -> 빈 리스트")
        void nullCurrent_empty() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10));
            assertThat(detector.detect("R1", new PopView.Pair(null, prev), NOW)).isEmpty();
        }

        @Test
        @DisplayName("pair.previous() == null -> 빈 리스트")
        void nullPrevious_empty() {
            PopView cur = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, toIntegers(THRESHOLD));
            assertThat(detector.detect("R1", new PopView.Pair(cur, null), NOW)).isEmpty();
        }
    }

    //  ---정상 onset 감지 ---

    @Nested
    @DisplayName("정상 onset 감지")
    class OnsetDetection {

        @Test
        @DisplayName("이전 POP<60, 현재 POP>=60 -> onset 감지")
        void prevBelowThreshold_curAbove_detected() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10, THRESHOLD-10, THRESHOLD-10));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD-10, THRESHOLD, THRESHOLD));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(2);
            assertThat(events).allMatch(e -> e.type() == AlertTypeEnum.RAIN_ONSET);
            assertThat(events.get(0).payload()).isInstanceOf(RainOnsetPayload.class);
        }

        @Test
        @DisplayName("이전 예보에 해당 validAt이 없으면 현재 POP 만으로 onset 판정")
        void noPrevData_curAboveThreshold_detected() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10, THRESHOLD-10));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD-10, THRESHOLD+10, THRESHOLD+10));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(2);
            assertThat(((RainOnsetPayload) events.get(0).payload()).validAt())
                    .isEqualTo(SERIES_START_TIME.plusHours(1));
            assertThat(((RainOnsetPayload) events.get(1).payload()).validAt())
                    .isEqualTo(SERIES_START_TIME.plusHours(2));
        }
    }

    // --- 비감지 케이스 ---

    @Nested
    @DisplayName("비감지 케이스")
    class NonOnsetCases {

        @Test
        @DisplayName("이전도 POP>=60, 현재도 POP>=60 -> onset 아님 (이미 비 상태)")
        void bothAbove_notOnset() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD, THRESHOLD, THRESHOLD));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD, THRESHOLD+10, THRESHOLD+10));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("이전 POP>=60, 현재 POP<60 -> onset 아님 (강수 소멸)")
        void prevAbove_curBelow_notOnset() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD, THRESHOLD, THRESHOLD+10));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD-10, 0, 0));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).isEmpty();
        }
    }

    //  --- 특수 입력 처리 (null 포인트 / maxHourlyPoints 절단) ---

    @Nested
    @DisplayName("특수 입력 처리")
    class SpecialInputHandling {

        @Test
        @DisplayName("현재 POP이 null인 포인트는 건너뛴다")
        void nullPop_skipped() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10, THRESHOLD-10));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, new Integer[]{null, THRESHOLD});

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(1);
            assertThat(((RainOnsetPayload) events.get(0).payload()).validAt())
                    .isEqualTo(SERIES_START_TIME.plusHours(1));
        }

        @Test
        @DisplayName("validAt이 null인 포인트는 건너뛰고, 이후 포인트는 계속 처리된다")
        void validAtNull_skippedAndLoopContinues() {
            List<Hourly.Pop> pops = new ArrayList<>();
            pops.add(new Hourly.Pop(SERIES_START_TIME,              THRESHOLD+10));
            pops.add(new Hourly.Pop(null,                    THRESHOLD+20));
            pops.add(new Hourly.Pop(SERIES_START_TIME.plusHours(2), THRESHOLD+10));
            for (int i = 3; i < 26; i++) {
                pops.add(new Hourly.Pop(SERIES_START_TIME.plusHours(i), null));
            }

            PopView cur  = new PopView(new Hourly(pops), buildEmptyDailySeries(), ANNOUNCE_TIME);
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10, THRESHOLD-10, THRESHOLD-10));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(2);
            assertThat(((RainOnsetPayload) events.get(0).payload()).validAt())
                    .isEqualTo(SERIES_START_TIME);
            assertThat(((RainOnsetPayload) events.get(1).payload()).validAt())
                    .isEqualTo(SERIES_START_TIME.plusHours(2));
        }

        @Test
        @DisplayName("maxHourlyPoints 초과 시 seen 카운터 기준으로 처리 포인트를 절단한다")
        void maxHourlyPoints_truncatesProcessing() {
            RainOnsetDetector smallDetector = new RainOnsetDetector(THRESHOLD, 3);

            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(0, 0, THRESHOLD-10, THRESHOLD-10));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD, THRESHOLD, THRESHOLD+10, THRESHOLD+10));

            List<AlertEvent> events = smallDetector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(3);
        }
    }

    //  --- 경계값 검증 ---

    @Nested
    @DisplayName("경계값 검증")
    class BoundaryValues {

        @Test
        @DisplayName("curPop이 정확히 threshold(60) -> onset 감지 (이상 포함)")
        void exactlyThreshold_isOnset() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-1));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(1);
            assertThat(((RainOnsetPayload) events.get(0).payload()).pop()).isEqualTo(THRESHOLD);
        }

        @Test
        @DisplayName("curPop=59 (threshold 미만) -> onset 아님")
        void belowThreshold_notOnset() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-20));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD-1));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("prevPop=60, curPop=60 -> onset 아님 (이전에도 이미 비)")
        void prevExactThreshold_curExactThreshold_notOnset() {
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD));
            PopView cur  = buildPopView(ANNOUNCE_TIME,               SERIES_START_TIME, toIntegers(THRESHOLD));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).isEmpty();
        }
    }

    //  --- occurredAt 계산 ---

    @Nested
    @DisplayName("occurredAt(computedAt) 계산 검증")
    class OccurredAt {

        @Test
        @DisplayName("reportTime이 있으면 truncateToMinutes(announceTime)이 occurredAt")
        void reportTimePresent_usedAsOccurredAt() {
            LocalDateTime reportTime = LocalDateTime.of(2026, 1, 22, 5, 30);
            PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, toIntegers(THRESHOLD-10));
            PopView cur  = buildPopView(reportTime,                  SERIES_START_TIME, toIntegers(THRESHOLD));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).occurredAt()).isEqualTo(reportTime);
        }

        @Test
        @DisplayName("reportTime이 null 이면 now가 occurredAt 으로 사용된다")
        void reportTimeNull_nowUsed() {
            PopView prev = buildPopView(null, SERIES_START_TIME, toIntegers(THRESHOLD-10));
            PopView cur  = buildPopView(null, SERIES_START_TIME, toIntegers(THRESHOLD));

            List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).occurredAt()).isEqualTo(NOW);
        }
    }

    //  --- 헬퍼 메서드 ---

    /** int 가변 인자를 Integer 배열로 변환한다. */
    private Integer[] toIntegers(int... values) {
        Integer[] result = new Integer[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }

    private PopView buildPopView(LocalDateTime reportTime,
                                 LocalDateTime seriesStart,
                                 Integer[] pops) {
        List<Hourly.Pop> hourlyPops = new ArrayList<>(MAX_POINTS);
        for (int i = 0; i < MAX_POINTS; i++) {
            Integer pop = (i < pops.length) ? pops[i] : null;
            hourlyPops.add(new Hourly.Pop(seriesStart.plusHours(i), pop));
        }
        return new PopView(new Hourly(hourlyPops), buildEmptyDailySeries(), reportTime);
    }

    private Daily buildEmptyDailySeries() {
        List<Daily.Pop> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) days.add(new Daily.Pop(null, null));
        return new Daily(days);
    }
}
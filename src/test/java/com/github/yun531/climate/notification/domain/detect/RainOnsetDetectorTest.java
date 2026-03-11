package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainOnsetDetectorTest {

    private static final int THRESHOLD = 60;
    private static final int MAX_POINTS = 26;

    private final RainOnsetDetector detector = new RainOnsetDetector(THRESHOLD, MAX_POINTS);

    private static final LocalDateTime ANNOUNCE_TIME     = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDateTime NOW               = LocalDateTime.of(2026, 1, 22, 5, 15);

    // validAt 시계열의 공통 기준 시각 (prev/cur 모두 동일한 validAt 사용)
    private static final LocalDateTime SERIES_START_TIME = ANNOUNCE_TIME.plusHours(1); // 06:00~

    @Test
    @DisplayName("이전 POP<60, 현재 POP>=60 → onset 감지")
    void prevBelowThreshold_curAbove_detected() {
        PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, pops(50, 50, 50));
        PopView cur  = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, pops(50, 70, 80));

        List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.type() == AlertTypeEnum.RAIN_ONSET);
        assertThat(events.get(0).payload()).isInstanceOf(RainOnsetPayload.class);
    }

    @Test
    @DisplayName("이전도 POP>=60, 현재도 POP>=60 → onset 아님 (이미 비 중)")
    void bothAbove_notOnset() {
        PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, pops(70, 70, 70));
        PopView cur  = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, pops(80, 80, 80));

        List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("이전 POP>=60 → 현재 POP<60 → onset 아님 (비 종료)")
    void prevAbove_curBelow_notOnset() {
        PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, pops(70, 80, 90));
        PopView cur  = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, pops(30, 20, 10));

        List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("이전 예보에 해당 validAt이 없으면 현재 POP 만으로 onset 판정")
    void noPrevData_curAboveThreshold_detected() {
        // prev: 06~07시만 데이터 있음, cur: 06~08시까지 데이터 있음
        // 08시(idx 2)는 prev에 없으므로 curPop 만으로 판정
        PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, pops(50, 50));
        PopView cur  = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, pops(50, 50, 70));

        List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

        assertThat(events).hasSize(1);
        RainOnsetPayload payload = (RainOnsetPayload) events.get(0).payload();
        assertThat(payload.validAt()).isEqualTo(SERIES_START_TIME.plusHours(2));
    }

    @Test
    @DisplayName("현재 POP이 null인 포인트는 건너뛴다")
    void nullPop_skipped() {
        PopView prev = buildPopView(ANNOUNCE_TIME.minusHours(3), SERIES_START_TIME, pops(50, 50));
        PopView cur  = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, new Integer[]{null, 70});

        List<AlertEvent> events = detector.detect("R1", new PopView.Pair(cur, prev), NOW);

        // null 포인트 건너뛰고, idx 1(70>=60, prev 50<60)만 onset
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("pair가 null 이면 빈 리스트")
    void nullPair_empty() {
        assertThat(detector.detect("R1", null, NOW)).isEmpty();
    }

    @Test
    @DisplayName("regionId가 blank 이면 빈 리스트")
    void blankRegion_empty() {
        PopView view = buildPopView(ANNOUNCE_TIME, SERIES_START_TIME, pops(70));
        assertThat(detector.detect("", new PopView.Pair(view, view), NOW)).isEmpty();
    }

    // -- 헬퍼 --

    private Integer[] pops(int... values) {
        Integer[] result = new Integer[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }

    private PopView buildPopView(LocalDateTime reportTime, LocalDateTime seriesStart, Integer[] firstPops) {
        List<HourlySeries.Point> points = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            Integer pop = (i < firstPops.length) ? firstPops[i] : null;
            points.add(new HourlySeries.Point(seriesStart.plusHours(i), pop));
        }

        List<DailySeries.DailyPop> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) days.add(new DailySeries.DailyPop(null, null));

        return new PopView(new HourlySeries(points), new DailySeries(days), reportTime);
    }
}
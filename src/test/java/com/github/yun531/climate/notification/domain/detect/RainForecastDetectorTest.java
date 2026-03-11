package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainForecastDetectorTest {

    private static final int THRESHOLD = 60;
    private static final int MAX_POINTS = 26;
    private final RainForecastDetector detector = new RainForecastDetector(THRESHOLD, MAX_POINTS);

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDateTime NOW           = LocalDateTime.of(2026, 1, 22, 5, 15);

    @Test
    @DisplayName("연속 비 구간 → hourlyParts에 RainInterval 생성")
    void consecutiveRain_producesInterval() {
        // idx 3,4,5: pop 70,80,90 → 연속 비 구간 1개
        Integer[] pops = {0, 0, 0, 70, 80, 90, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
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
    @DisplayName("비 구간이 여러 개 (끊어진 비)")
    void multipleRainSegments() {
        // idx 0,1: 비 / idx 2,3: 맑음 / idx 4,5: 비
        Integer[] pops = {80, 70, 0, 0, 90, 60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        PopView view = buildPopView(ANNOUNCE_TIME, pops);

        AlertEvent event = detector.detect("R1", view, NOW);
        RainForecastPayload payload = (RainForecastPayload) event.payload();

        assertThat(payload.hourlyParts()).hasSize(2);
    }

    @Test
    @DisplayName("비 없음 → hourlyParts 비어있음")
    void noRain_emptyHourlyParts() {
        Integer[] pops = new Integer[26]; // 전부 null
        PopView view = buildPopView(ANNOUNCE_TIME, pops);

        AlertEvent event = detector.detect("R1", view, NOW);
        RainForecastPayload payload = (RainForecastPayload) event.payload();

        assertThat(payload.hourlyParts()).isEmpty();
    }

    @Test
    @DisplayName("null POP → 비 구간에서 제외 (비 구간 끊김)")
    void nullPop_breaksRainSegment() {
        // idx 0: 80(비), idx 1: null(끊김), idx 2: 70(비)
        Integer[] pops = new Integer[26];
        pops[0] = 80;
        pops[1] = null;
        pops[2] = 70;
        PopView view = buildPopView(ANNOUNCE_TIME, pops);

        AlertEvent event = detector.detect("R1", view, NOW);
        RainForecastPayload payload = (RainForecastPayload) event.payload();

        // null이 구간을 끊으므로 2개 독립 구간
        assertThat(payload.hourlyParts()).hasSize(2);
    }

    @Test
    @DisplayName("dailyPop → dayFlags AM/PM 매핑")
    void dailyFlags_amPmMapping() {
        Integer[] pops = new Integer[26];
        PopView view = buildPopViewWithDaily(ANNOUNCE_TIME, pops, 70, 30); // day0: am>=60, pm<60

        AlertEvent event = detector.detect("R1", view, NOW);
        RainForecastPayload payload = (RainForecastPayload) event.payload();

        assertThat(payload.dayParts()).isNotEmpty();
        assertThat(payload.dayParts().get(0).rainAm()).isTrue();
        assertThat(payload.dayParts().get(0).rainPm()).isFalse();
    }

    @Test
    @DisplayName("dailyPop이 null → rainAm/rainPm 모두 false")
    void nullDailyPop_allFalse() {
        Integer[] pops = new Integer[26];
        PopView view = buildPopViewWithDaily(ANNOUNCE_TIME, pops, null, null);

        AlertEvent event = detector.detect("R1", view, NOW);
        RainForecastPayload payload = (RainForecastPayload) event.payload();

        assertThat(payload.dayParts().get(0).rainAm()).isFalse();
        assertThat(payload.dayParts().get(0).rainPm()).isFalse();
    }

    @Test
    @DisplayName("null 입력 → null 반환")
    void nullInputs_returnsNull() {
        assertThat(detector.detect(null, buildPopView(ANNOUNCE_TIME, new Integer[26]), NOW)).isNull();
        assertThat(detector.detect("R1", null, NOW)).isNull();
        assertThat(detector.detect("R1", buildPopView(ANNOUNCE_TIME, new Integer[26]), null)).isNull();
    }

    // -- 헬퍼 --

    private PopView buildPopView(LocalDateTime reportTime, Integer[] pops) {
        return buildPopViewWithDaily(reportTime, pops, null, null);
    }

    private PopView buildPopViewWithDaily(LocalDateTime reportTime, Integer[] pops,
                                          Integer day0Am, Integer day0Pm) {
        List<HourlySeries.Point> points = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            Integer pop = (i < pops.length) ? pops[i] : null;
            points.add(new HourlySeries.Point(reportTime.plusHours(i + 1), pop));
        }

        List<DailySeries.DailyPop> days = new ArrayList<>(7);
        days.add(new DailySeries.DailyPop(day0Am, day0Pm));
        for (int i = 1; i < 7; i++) days.add(new DailySeries.DailyPop(null, null));

        return new PopView(new HourlySeries(points), new DailySeries(days), reportTime);
    }
}
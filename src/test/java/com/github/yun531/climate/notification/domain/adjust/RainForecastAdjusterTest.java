package com.github.yun531.climate.notification.domain.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.RainInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainForecastAdjusterTest {

    // maxShiftHours=2, horizonHours=24, startOffsetHours=1
    private final RainForecastAdjuster adjuster = new RainForecastAdjuster(2, 24, 1);

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("시프트 없음 (now == announceTime) → 윈도우 클리핑만 수행")
    void noShift_clippingOnly() {
        LocalDateTime now = ANNOUNCE_TIME;
        // windowStart = 05:00 + 1 = 06:00, windowEnd = 05:00 + 24 = 29:00
        RainInterval inside  = new RainInterval(ANNOUNCE_TIME.plusHours(2), ANNOUNCE_TIME.plusHours(5));   // 07~10 → 포함
        RainInterval outside = new RainInterval(ANNOUNCE_TIME.plusHours(25), ANNOUNCE_TIME.plusHours(26)); // 30~31 → 제외

        AlertEvent event = makeAlertEvent(List.of(inside, outside));
        AlertEvent result = adjuster.adjust(event, ANNOUNCE_TIME, now);

        RainForecastPayload payload = (RainForecastPayload) result.payload();
        assertThat(payload.hourlyParts()).hasSize(1);
        assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(2));
    }

    @Test
    @DisplayName("1시간 시프트 → 윈도우 start가 이동")
    void oneHourShift_windowMoves() {
        LocalDateTime now = ANNOUNCE_TIME.plusHours(1); // 06:00
        // windowStart = 06:00 + 1(startOffset) = 07:00
        RainInterval outside = new RainInterval(ANNOUNCE_TIME, ANNOUNCE_TIME.plusHours(1));               // 05~06 → 윈도우 밖
        RainInterval inside  = new RainInterval(ANNOUNCE_TIME.plusHours(4), ANNOUNCE_TIME.plusHours(6));   // 09~11 → 윈도우 안

        AlertEvent event = makeAlertEvent(List.of(outside, inside));
        AlertEvent result = adjuster.adjust(event, ANNOUNCE_TIME, now);

        RainForecastPayload payload = (RainForecastPayload) result.payload();
        assertThat(payload.hourlyParts()).hasSize(1);
        assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(4));
    }

    @Test
    @DisplayName("윈도우 경계에 걸치는 구간 → 클리핑")
    void overlappingInterval_isClamped() {
        LocalDateTime now = ANNOUNCE_TIME.plusHours(1); // 06:00
        // windowStart = 07:00
        RainInterval overlapping = new RainInterval(ANNOUNCE_TIME.plusHours(1), ANNOUNCE_TIME.plusHours(4)); // 06~09 → 07~09로 클리핑

        AlertEvent event = makeAlertEvent(List.of(overlapping));
        AlertEvent result = adjuster.adjust(event, ANNOUNCE_TIME, now);

        RainForecastPayload payload = (RainForecastPayload) result.payload();
        assertThat(payload.hourlyParts()).hasSize(1);
        assertThat(payload.hourlyParts().get(0).start()).isEqualTo(ANNOUNCE_TIME.plusHours(2)); // 07:00
        assertThat(payload.hourlyParts().get(0).end()).isEqualTo(ANNOUNCE_TIME.plusHours(4));   // 09:00
    }

    @Test
    @DisplayName("dayShift 발생 시 dayParts 앞쪽 드롭")
    void dayShift_dropsFrontDays() {
        // 23시 발표 → now 01시(+2h) → 날짜 경계를 넘어 dayShift=1
        LocalDateTime announceTime = LocalDateTime.of(2026, 1, 22, 23, 0);
        LocalDateTime now          = announceTime.plusHours(2); // 01:00 (1/23)

        List<DailyRainFlags> days = List.of(
                new DailyRainFlags(true, false),   // day0 → 드롭
                new DailyRainFlags(false, true),   // day1 → day0으로
                new DailyRainFlags(true, true),    // day2 → day1로
                new DailyRainFlags(false, false),
                new DailyRainFlags(false, false),
                new DailyRainFlags(false, false),
                new DailyRainFlags(false, false)
        );

        AlertEvent event = makeAlertEvent(List.of(), days);
        AlertEvent result = adjuster.adjust(event, announceTime, now);

        RainForecastPayload payload = (RainForecastPayload) result.payload();
        assertThat(payload.dayParts().get(0).rainAm()).isFalse();  // 원래 day1
        assertThat(payload.dayParts().get(0).rainPm()).isTrue();   // 원래 day1
    }

    @Test
    @DisplayName("null event → null 반환")
    void nullEvent_returnsNull() {
        assertThat(adjuster.adjust(null, ANNOUNCE_TIME, ANNOUNCE_TIME)).isNull();
    }

    @Test
    @DisplayName("null announceTime → 보정 없이 원본 반환")
    void nullAnnounceTime_returnsOriginal() {
        AlertEvent event = makeAlertEvent(List.of());
        AlertEvent result = adjuster.adjust(event, null, ANNOUNCE_TIME);
        assertThat(result).isEqualTo(event);
    }

    // -- 헬퍼 --

    private static final List<DailyRainFlags> EMPTY_DAYS = List.of(
            new DailyRainFlags(false, false), new DailyRainFlags(false, false),
            new DailyRainFlags(false, false), new DailyRainFlags(false, false),
            new DailyRainFlags(false, false), new DailyRainFlags(false, false),
            new DailyRainFlags(false, false)
    );

    private AlertEvent makeAlertEvent(List<RainInterval> hourly) {
        return makeAlertEvent(hourly, EMPTY_DAYS);
    }

    private AlertEvent makeAlertEvent(List<RainInterval> hourly, List<DailyRainFlags> days) {
        RainForecastPayload payload = new RainForecastPayload(AlertTypeEnum.RAIN_FORECAST, hourly, days);
        return new AlertEvent(AlertTypeEnum.RAIN_FORECAST, "R1", ANNOUNCE_TIME, payload);
    }
}
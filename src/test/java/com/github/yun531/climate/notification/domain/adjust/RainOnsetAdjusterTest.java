package com.github.yun531.climate.notification.domain.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainOnsetAdjusterTest {

    private final RainOnsetAdjuster adjuster = new RainOnsetAdjuster(24, 1);

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("윈도우 내 이벤트만 남김 (now+1 ~ now+24)")
    void withinWindow_kept() {
        AlertEvent inside  = makeEvent(NOW.plusHours(3));
        AlertEvent outside = makeEvent(NOW.plusHours(25));

        List<AlertEvent> result = adjuster.adjust(List.of(inside, outside), NOW, null);

        assertThat(result).hasSize(1);
        assertThat(((RainOnsetPayload) result.get(0).payload()).validAt()).isEqualTo(NOW.plusHours(3));
    }

    @Test
    @DisplayName("now+1 미만(즉시 시간대) 이벤트 제외")
    void beforeWindowStart_excluded() {
        AlertEvent tooSoon = makeEvent(NOW.plusMinutes(30)); // now+0.5h < now+1h

        List<AlertEvent> result = adjuster.adjust(List.of(tooSoon), NOW, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("hourLimit 적용 시 윈도우 축소")
    void hourLimit_shrinksWindow() {
        AlertEvent at3h = makeEvent(NOW.plusHours(3));
        AlertEvent at5h = makeEvent(NOW.plusHours(5));

        List<AlertEvent> result = adjuster.adjust(List.of(at3h, at5h), NOW, 4);

        assertThat(result).hasSize(1); // at3h만 (now+1 ~ now+4)
    }

    @Test
    @DisplayName("occurredAt이 nowHour로 통일됨")
    void occurredAt_normalizedToNowHour() {
        AlertEvent event = makeEvent(NOW.plusHours(2));

        List<AlertEvent> result = adjuster.adjust(List.of(event), NOW, null);

        assertThat(result.get(0).occurredAt()).isEqualTo(NOW); // truncatedTo(HOURS)
    }

    @Test
    @DisplayName("빈 리스트 → 빈 리스트")
    void emptyInput_emptyOutput() {
        assertThat(adjuster.adjust(List.of(), NOW, null)).isEmpty();
    }

    @Test
    @DisplayName("null 리스트 → 빈 리스트")
    void nullInput_emptyOutput() {
        assertThat(adjuster.adjust(null, NOW, null)).isEmpty();
    }

    // -- 헬퍼 --

    private AlertEvent makeEvent(LocalDateTime validAt) {
        return new AlertEvent(
                AlertTypeEnum.RAIN_ONSET, "R1", NOW,
                new RainOnsetPayload(AlertTypeEnum.RAIN_ONSET, validAt, 80)
        );
    }
}

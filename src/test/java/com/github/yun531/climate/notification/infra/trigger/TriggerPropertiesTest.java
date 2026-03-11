package com.github.yun531.climate.notification.infra.trigger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerPropertiesTest {

    private final TriggerProperties props = new TriggerProperties("hourly", "daily_", 600);

    @Test
    @DisplayName("dailyTopic — 한자리 hour은 0-패딩 2자리로 포맷")
    void dailyTopic_singleDigit_zeroPadded() {
        assertThat(props.dailyTopic(8)).isEqualTo("daily_08");
    }

    @Test
    @DisplayName("dailyTopic — 두자리 hour은 그대로 포맷")
    void dailyTopic_doubleDigit() {
        assertThat(props.dailyTopic(17)).isEqualTo("daily_17");
    }

    @Test
    @DisplayName("ttlMillis — ttlSeconds * 1000")
    void ttlMillis_conversion() {
        assertThat(props.ttlMillis()).isEqualTo(600_000);
    }

    @Test
    @DisplayName("ttlSeconds 음수 -> 기본값 600으로 보정")
    void negativeTtl_defaultsTo600() {
        TriggerProperties negative = new TriggerProperties("h", "d_", -1);
        assertThat(negative.ttlSeconds()).isEqualTo(600);
    }
}

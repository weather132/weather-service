package com.github.yun531.climate.fcm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicPushMessageTest {

    @Nested
    @DisplayName("생성 검증")
    class Construction {

        @Test
        @DisplayName("정상 생성 — topic, data, ttl 모두 설정")
        void normalCreation() {
            Map<String, String> data = Map.of("type", "HOURLY_TRIGGER", "hour", "8");
            TopicPushMessage msg = new TopicPushMessage("hourly", data, 600_000);

            assertThat(msg.topic()).isEqualTo("hourly");
            assertThat(msg.data()).containsEntry("type", "HOURLY_TRIGGER");
            assertThat(msg.data()).containsEntry("hour", "8");
            assertThat(msg.ttlMillis()).isEqualTo(600_000);
        }

        @Test
        @DisplayName("topic이 null 이면 IllegalArgumentException")
        void nullTopic_throws() {
            assertThatThrownBy(() -> new TopicPushMessage(null, Map.of(), 1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("topic이 blank 이면 IllegalArgumentException")
        void blankTopic_throws() {
            assertThatThrownBy(() -> new TopicPushMessage("  ", Map.of(), 1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("data가 null 이면 빈 Map 으로 정규화")
        void nullData_normalizedToEmptyMap() {
            TopicPushMessage msg = new TopicPushMessage("hourly", null, 1000);

            assertThat(msg.data()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("ttlMillis가 음수이면 0으로 정규화")
        void negativeTtl_normalizedToZero() {
            TopicPushMessage msg = new TopicPushMessage("hourly", Map.of(), -100);

            assertThat(msg.ttlMillis()).isZero();
        }

        @Test
        @DisplayName("ttlMillis가 0이면 그대로 유지")
        void zeroTtl_staysZero() {
            TopicPushMessage msg = new TopicPushMessage("hourly", Map.of(), 0);

            assertThat(msg.ttlMillis()).isZero();
        }
    }

    @Nested
    @DisplayName("불변성")
    class Immutability {

        @Test
        @DisplayName("data Map은 방어적 복사 — 외부 수정 불가")
        void dataMap_defensiveCopy() {
            var original = new java.util.HashMap<String, String>();
            original.put("key", "value");

            TopicPushMessage msg = new TopicPushMessage("hourly", original, 1000);

            // 원본 변경해도 msg.data()에 영향 없음
            original.put("key2", "value2");
            assertThat(msg.data()).doesNotContainKey("key2");
        }

        @Test
        @DisplayName("data() 반환값 수정 시도 -> UnsupportedOperationException")
        void dataMap_unmodifiable() {
            TopicPushMessage msg = new TopicPushMessage("hourly", Map.of("k", "v"), 1000);

            assertThatThrownBy(() -> msg.data().put("new", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
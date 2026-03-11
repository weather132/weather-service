package com.github.yun531.climate.notification.infra.trigger;

import com.github.yun531.climate.fcm.domain.TopicPushMessage;
import com.github.yun531.climate.fcm.domain.TopicPushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmTriggerPushSenderTest {

    @Mock TopicPushSender pushSender;
    // pushSender.push()에 넘겨진 실제 TopicPushMessage 인자를 캡처해 검증에 재사용
    @Captor ArgumentCaptor<TopicPushMessage> messageCaptor;

    private FcmTriggerPushSender sender;

    private static final LocalDateTime TRIGGER_TIME = LocalDateTime.of(2026, 1, 22, 8, 5);

    @BeforeEach
    void setUp() {
        TriggerProperties props = new TriggerProperties("hourly", "daily_", 600);
        sender = new FcmTriggerPushSender(pushSender, props);
    }

    @Nested
    @DisplayName("sendHourly")
    class SendHourly {

        @Test
        @DisplayName("topic이 'hourly' 로 설정된다")
        void topicIsHourly() {
            when(pushSender.push(messageCaptor.capture(), eq(false))).thenReturn("msg-001");

            sender.sendHourly(TRIGGER_TIME, 8, false);

            TopicPushMessage msg = messageCaptor.getValue();
            assertThat(msg.topic()).isEqualTo("hourly");
        }

        @Test
        @DisplayName("data에 type, triggerAtLocal, hour 키가 포함된다")
        void dataKeysPopulated() {
            when(pushSender.push(messageCaptor.capture(), anyBoolean())).thenReturn("msg-001");

            sender.sendHourly(TRIGGER_TIME, 8, true);

            TopicPushMessage msg = messageCaptor.getValue();
            assertThat(msg.data())
                    .containsEntry("type", "HOURLY_TRIGGER")
                    .containsEntry("hour", "8")
                    .containsKey("triggerAtLocal");
        }

        @Test
        @DisplayName("triggerAtLocal은 ISO 형식으로 포맷된다")
        void triggerAtLocal_isoFormat() {
            when(pushSender.push(messageCaptor.capture(), anyBoolean())).thenReturn("msg-001");

            sender.sendHourly(TRIGGER_TIME, 8, false);

            String triggerAt = messageCaptor.getValue().data().get("triggerAtLocal");
            assertThat(triggerAt).isEqualTo("2026-01-22T08:05:00");
        }

        @Test
        @DisplayName("ttlMillis = ttlSeconds * 1000")
        void ttlConversion() {
            when(pushSender.push(messageCaptor.capture(), anyBoolean())).thenReturn("msg-001");

            sender.sendHourly(TRIGGER_TIME, 8, false);

            assertThat(messageCaptor.getValue().ttlMillis()).isEqualTo(600_000);
        }

        @Test
        @DisplayName("dryRun 플래그가 pushSender에 전달된다")
        void dryRunPropagated() {
            when(pushSender.push(messageCaptor.capture(), eq(true))).thenReturn("dry-001");

            String result = sender.sendHourly(TRIGGER_TIME, 8, true);

            assertThat(result).isEqualTo("dry-001");
            verify(pushSender).push(messageCaptor.getValue(), true);
        }

        @Test
        @DisplayName("pushSender의 반환값(messageId)을 그대로 반환한다")
        void returnsMessageId() {
            when(pushSender.push(messageCaptor.capture(), anyBoolean())).thenReturn("msg-123");

            String result = sender.sendHourly(TRIGGER_TIME, 8, false);

            assertThat(result).isEqualTo("msg-123");
        }
    }

    @Nested
    @DisplayName("sendDaily")
    class SendDaily {

        @Test
        @DisplayName("topic은 TriggerProperties.dailyTopic(hour) 결과를 사용한다")
        void topicDelegatedToProperties() {
            when(pushSender.push(messageCaptor.capture(), anyBoolean())).thenReturn("msg-001");

            sender.sendDaily(TRIGGER_TIME, 8, false);

            assertThat(messageCaptor.getValue().topic())
                    .isEqualTo(new TriggerProperties("hourly", "daily_", 600).dailyTopic(8));
        }

        @Test
        @DisplayName("data에 DAILY_TRIGGER 타입이 설정된다")
        void dataType_isDailyTrigger() {
            when(pushSender.push(messageCaptor.capture(), anyBoolean())).thenReturn("msg-001");

            sender.sendDaily(TRIGGER_TIME, 8, false);

            assertThat(messageCaptor.getValue().data()).containsEntry("type", "DAILY_TRIGGER");
        }

        @Test
        @DisplayName("hour가 음수이면 IllegalArgumentException")
        void negativeHour_throws() {
            assertThatThrownBy(() -> sender.sendDaily(TRIGGER_TIME, -1, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("hour가 24이면 IllegalArgumentException")
        void hour24_throws() {
            assertThatThrownBy(() -> sender.sendDaily(TRIGGER_TIME, 24, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
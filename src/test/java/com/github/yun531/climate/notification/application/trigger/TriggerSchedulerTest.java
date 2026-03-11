package com.github.yun531.climate.notification.application.trigger;

import com.github.yun531.climate.fcm.domain.PushFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerSchedulerTest {

    @Mock TriggerPushSender sender;
    @InjectMocks TriggerScheduler scheduler;

    @Nested
    @DisplayName("triggerHourly")
    class TriggerHourly {

        @Test
        @DisplayName("sender.sendHourly에 위임한다")
        void delegatesToSender() {
            when(sender.sendHourly(any(), anyInt(), anyBoolean())).thenReturn("msg-001");

            scheduler.triggerHourly();

            verify(sender).sendHourly(any(), anyInt(), anyBoolean());
        }

        @Test
        @DisplayName("sender 예외 발생 시 스케줄러가 죽지 않는다 (catch 처리)")
        void senderThrows_schedulerSurvives() {
            when(sender.sendHourly(any(), anyInt(), anyBoolean()))
                    .thenThrow(new PushFailedException("FCM error"));

            assertThatNoException().isThrownBy(() -> scheduler.triggerHourly());
        }
    }

    @Nested
    @DisplayName("triggerDaily")
    class TriggerDaily {

        @Test
        @DisplayName("sender.sendDaily에 위임한다")
        void delegatesToSender() {
            when(sender.sendDaily(any(), anyInt(), anyBoolean())).thenReturn("msg-002");

            scheduler.triggerDaily();

            verify(sender).sendDaily(any(), anyInt(), anyBoolean());
        }

        @Test
        @DisplayName("sender 예외 발생 시 스케줄러가 죽지 않는다 (catch 처리)")
        void senderThrows_schedulerSurvives() {
            when(sender.sendDaily(any(), anyInt(), anyBoolean()))
                    .thenThrow(new PushFailedException("FCM error"));

            assertThatNoException().isThrownBy(() -> scheduler.triggerDaily());
        }
    }
}
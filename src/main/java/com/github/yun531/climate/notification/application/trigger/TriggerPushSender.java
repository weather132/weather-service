package com.github.yun531.climate.notification.application.trigger;

import java.time.LocalDateTime;

/**
 * 트리거 푸시 전송 계약.
 * - notification이 "어떤 의도로 보내는가"만 표현
 */
public interface TriggerPushSender {

    String sendHourly(LocalDateTime firedAt, int hour, boolean dryRun);

    String sendDaily(LocalDateTime firedAt, int hour, boolean dryRun);
}
